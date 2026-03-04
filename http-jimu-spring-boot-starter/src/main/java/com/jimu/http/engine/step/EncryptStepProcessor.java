package com.jimu.http.engine.step;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.jimu.http.engine.support.JimuExpressionResolver;
import com.jimu.http.model.enums.StepType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

@Component
@RequiredArgsConstructor
public class EncryptStepProcessor implements StepProcessor {

    private final JimuExpressionResolver expressionResolver;

    @Override
    public StepType getType() {
        return StepType.ENCRYPT;
    }

    @Override
    public Object process(Object target, Map<String, Object> config, StepContext stepContext) {
        if (!(target instanceof Map)) return target;
        if (config == null) config = Collections.emptyMap();
        Map<String, Object> map = (Map<String, Object>) target;
        Map<String, Object> context = stepContext.getContext();

        String algorithm = resolveConfigString(config, "algorithm", context, "HMAC_SHA256");
        String outputEncoding = resolveConfigString(config, "outputEncoding", context, "BASE64");
        boolean overwrite = Boolean.parseBoolean(resolveConfigString(config, "overwrite", context, "true"));
        String targetField = resolveConfigString(config, "targetField", context, "");
        List<String> fields = extractFields(config.get("fields"), context);

        if (fields.isEmpty()) {
            String sourceField = resolveConfigString(config, "sourceField", context, "");
            if (StrUtil.isNotBlank(sourceField)) {
                fields = Collections.singletonList(sourceField);
            }
        }

        if (fields.isEmpty()) {
            String payload = JSON.toJSONString(new TreeMap<>(map));
            String result = cryptoTransform(payload, algorithm, config, context, outputEncoding);
            map.put(StrUtil.isNotBlank(targetField) ? targetField : "encrypted", result);
            return map;
        }

        for (String field : fields) {
            if (!map.containsKey(field)) continue;
            Object raw = map.get(field);
            String plain = raw == null ? "" : String.valueOf(raw);
            String encrypted = cryptoTransform(plain, algorithm, config, context, outputEncoding);

            if (overwrite) {
                map.put(field, encrypted);
                continue;
            }

            String outputField = targetField;
            if (StrUtil.isBlank(outputField)) {
                outputField = field + "_enc";
            } else if (fields.size() > 1) {
                outputField = outputField + "_" + field;
            }
            map.put(outputField, encrypted);
        }
        return map;
    }

    private String cryptoTransform(String plain, String algorithm, Map<String, Object> config, Map<String, Object> context, String outputEncoding) {
        String normalized = algorithm == null ? "" : algorithm.trim().toUpperCase(Locale.ROOT);
        try {
            byte[] bytes;
            switch (normalized) {
                case "HMAC_SHA1":
                    bytes = hmac(plain, resolveRequiredConfig(config, "secret", context), "HmacSHA1");
                    return encodeOutput(bytes, outputEncoding);
                case "HMAC_SHA256":
                    bytes = hmac(plain, resolveRequiredConfig(config, "secret", context), "HmacSHA256");
                    return encodeOutput(bytes, outputEncoding);
                case "AES_ECB_PKCS5":
                    bytes = aesEncrypt(plain, resolveRequiredConfig(config, "secret", context), null, "AES/ECB/PKCS5Padding");
                    return encodeOutput(bytes, outputEncoding);
                case "AES_CBC_PKCS5":
                    String iv = resolveRequiredConfig(config, "iv", context);
                    bytes = aesEncrypt(plain, resolveRequiredConfig(config, "secret", context), iv, "AES/CBC/PKCS5Padding");
                    return encodeOutput(bytes, outputEncoding);
                case "RSA_ECB_PKCS1":
                    bytes = rsaEncryptByPublicKey(plain, resolveRequiredConfig(config, "publicKey", context));
                    return encodeOutput(bytes, outputEncoding);
                case "RSA_SHA256_SIGN":
                    bytes = rsaSha256Sign(plain, resolveRequiredConfig(config, "privateKey", context));
                    return encodeOutput(bytes, outputEncoding);
                default:
                    throw new IllegalArgumentException("Unsupported ENCRYPT algorithm: " + algorithm);
            }
        } catch (Exception e) {
            throw new RuntimeException("ENCRYPT step failed: " + e.getMessage(), e);
        }
    }

    private byte[] hmac(String content, String secret, String macAlgorithm) throws Exception {
        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] aesEncrypt(String content, String secret, String iv, String transformation) throws Exception {
        byte[] keyBytes = normalizeAesKey(secret.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance(transformation);
        if (transformation.contains("CBC")) {
            byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
            if (ivBytes.length != 16) {
                throw new IllegalArgumentException("AES CBC iv length must be 16 bytes");
            }
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(ivBytes));
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        }
        return cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] rsaEncryptByPublicKey(String content, String publicPem) throws Exception {
        PublicKey publicKey = parsePublicKey(publicPem);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] rsaSha256Sign(String content, String privatePem) throws Exception {
        PrivateKey privateKey = parsePrivateKey(privatePem);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    private PublicKey parsePublicKey(String pem) throws Exception {
        String normalized = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private byte[] normalizeAesKey(byte[] original) {
        int[] validLens = {16, 24, 32};
        for (int len : validLens) {
            if (original.length == len) return original;
        }
        if (original.length > 32) {
            return Arrays.copyOf(original, 32);
        }
        if (original.length > 24) {
            return Arrays.copyOf(original, 32);
        }
        if (original.length > 16) {
            return Arrays.copyOf(original, 24);
        }
        return Arrays.copyOf(original, 16);
    }

    private String encodeOutput(byte[] bytes, String outputEncoding) {
        String mode = outputEncoding == null ? "BASE64" : outputEncoding.trim().toUpperCase(Locale.ROOT);
        if ("HEX".equals(mode)) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String resolveRequiredConfig(Map<String, Object> config, String key, Map<String, Object> context) {
        String val = resolveConfigString(config, key, context, "");
        if (StrUtil.isBlank(val)) {
            throw new IllegalArgumentException("ENCRYPT config missing: " + key);
        }
        return val;
    }

    private String resolveConfigString(Map<String, Object> config, String key, Map<String, Object> context, String defaultValue) {
        if (config == null || StrUtil.isBlank(key)) return defaultValue;
        Object raw = config.get(key);
        if (raw == null) return defaultValue;
        return expressionResolver.resolve(String.valueOf(raw), context);
    }

    private List<String> extractFields(Object rawFields, Map<String, Object> context) {
        if (rawFields == null) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        if (rawFields instanceof Collection<?>) {
            for (Object field : (Collection<?>) rawFields) {
                if (field != null) {
                    String value = expressionResolver.resolve(String.valueOf(field), context);
                    if (StrUtil.isNotBlank(value)) result.add(value.trim());
                }
            }
            return result;
        }
        String text = expressionResolver.resolve(String.valueOf(rawFields), context);
        if (StrUtil.isBlank(text)) return result;
        if (text.trim().startsWith("[")) {
            try {
                List<String> arr = JSON.parseArray(text, String.class);
                if (arr != null) {
                    for (String item : arr) {
                        if (StrUtil.isNotBlank(item)) result.add(item.trim());
                    }
                }
                return result;
            } catch (Exception ignore) {
                // fallback to comma split
            }
        }
        for (String piece : text.split(",")) {
            if (StrUtil.isNotBlank(piece)) result.add(piece.trim());
        }
        return result;
    }
}
