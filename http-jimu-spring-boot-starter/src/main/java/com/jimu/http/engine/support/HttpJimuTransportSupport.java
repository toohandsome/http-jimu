package com.jimu.http.engine.support;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jimu.http.engine.model.ExecuteDetail;
import com.jimu.http.entity.HttpJimuConfig;
import com.jimu.http.entity.HttpJimuPool;
import com.jimu.http.exception.JimuNetworkException;
import com.jimu.http.service.HttpJimuPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpJimuTransportSupport {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    private static final int DEFAULT_WRITE_TIMEOUT_MS = 10000;
    private static final int DEFAULT_CALL_TIMEOUT_MS = 0;
    private static final int DEFAULT_MAX_REQUESTS = 64;
    private static final int DEFAULT_MAX_REQUESTS_PER_HOST = 5;
    private static final int DEFAULT_PING_INTERVAL_MS = 0;
    private static final long DEFAULT_KEEP_ALIVE_MS = 300000L;
    private static final int DEFAULT_MAX_IDLE_CONNECTIONS = 5;

    private final HttpJimuPoolService poolService;

    private final Map<String, OkHttpClient> clientPools = new ConcurrentHashMap<>();
    // FIX (Issue 5 & 8): Bounded cache for dynamic clients to prevent OOM
    private final Cache<String, OkHttpClient> overrideClients =
            Caffeine.newBuilder()
                    .maximumSize(1000)
                    .expireAfterAccess(1, TimeUnit.HOURS)
                    .build();
    private final OkHttpClient defaultClient = new OkHttpClient();

    public void evictClientPool(String poolId) {
        if (StrUtil.isBlank(poolId)) {
            return;
        }
        OkHttpClient client = clientPools.remove(poolId);
        if (client != null) {
            client.connectionPool().evictAll();
        }
    }

    public String mergeUrlQueryParams(String url, Map<String, String> queryParams) {
        if (StrUtil.isBlank(url)) {
            return url;
        }
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        try {
            URI uri = URI.create(url);
            Map<String, String> merged = parseQuery(uri.getRawQuery());
            merged.putAll(queryParams);

            String query = merged.entrySet().stream()
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));

            URI rebuilt = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), query, uri.getFragment());
            return rebuilt.toString();
        } catch (Exception e) {
            log.warn("Failed to merge query params, fallback append: {}", e.getMessage());
            String queryString = queryParams.entrySet().stream()
                    .map(e2 -> e2.getKey() + "=" + HttpUtil.encodeParams(e2.getValue(), CharsetUtil.CHARSET_UTF_8))
                    .collect(Collectors.joining("&"));
            return url.contains("?") ? url + "&" + queryString : url + "?" + queryString;
        }
    }

    public ExecuteDetail sendRequestWithDetail(HttpJimuConfig config,
                                               String method,
                                               String url,
                                               Map<String, String> headers,
                                               Object body) {
        OkHttpClient client = getClient(config);
        long start = System.currentTimeMillis();
        ExecuteDetail detail = new ExecuteDetail();

        String upperMethod = StrUtil.isBlank(method) ? "POST" : method.trim().toUpperCase(Locale.ROOT);
        Request.Builder requestBuilder = new Request.Builder().url(url);
        detail.setRequestMethod(upperMethod);
        detail.setRequestUrl(url);

        Map<String, String> reqHeaders = new LinkedHashMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
                reqHeaders.put(entry.getKey(), entry.getValue());
            }
        }
        detail.setRequestHeaders(reqHeaders);

        RequestBody requestBody = null;
        String requestBodyText;
        String bodyType = config.getBodyType();
        String headerContentType = getHeaderIgnoreCase(headers, "Content-Type");

        if (!"GET".equals(upperMethod) && !"HEAD".equals(upperMethod)) {
            if (body != null) {
                if (StrUtil.equals(bodyType, "form-data")) {
                    MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
                    Map<String, String> formPairs = new LinkedHashMap<>();
                    if (body instanceof Map<?, ?> map) {
                        map.forEach((k, v) -> {
                            String key = String.valueOf(k);
                            String val = String.valueOf(v);
                            builder.addFormDataPart(key, val);
                            formPairs.put(key, val);
                        });
                    }
                    requestBody = builder.build();
                    requestBodyText = formPairs.entrySet().stream()
                            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                            .collect(Collectors.joining("&"));
                    if (requestBody.contentType() != null) {
                        requestBuilder.header("Content-Type", requestBody.contentType().toString());
                        reqHeaders.put("Content-Type", requestBody.contentType().toString());
                    }
                } else if (StrUtil.equals(bodyType, "x-www-form-urlencoded")) {
                    FormBody.Builder builder = new FormBody.Builder();
                    Map<String, String> formPairs = new LinkedHashMap<>();
                    if (body instanceof Map<?, ?> map) {
                        map.forEach((k, v) -> {
                            String key = String.valueOf(k);
                            String val = String.valueOf(v);
                            builder.add(key, val);
                            formPairs.put(key, val);
                        });
                    }
                    requestBody = builder.build();
                    requestBodyText = formPairs.entrySet().stream()
                            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                            .collect(Collectors.joining("&"));
                    if (requestBody.contentType() != null) {
                        requestBuilder.header("Content-Type", requestBody.contentType().toString());
                        reqHeaders.put("Content-Type", requestBody.contentType().toString());
                    }
                } else {
                    MediaType mediaType = headerContentType != null
                            ? MediaType.parse(headerContentType)
                            : resolveRawMediaType(config.getBodyRawType());
                    if (mediaType == null) {
                        mediaType = MediaType.parse("application/json; charset=utf-8");
                    }
                    String content = (body instanceof Map || body instanceof List)
                            ? JSON.toJSONString(body)
                            : String.valueOf(body);
                    requestBodyText = content;
                    requestBody = RequestBody.create(content, mediaType);
                }
            } else {
                requestBody = RequestBody.create(new byte[0], null);
                requestBodyText = "";
            }
            requestBuilder.method(upperMethod, requestBody);
        } else {
            requestBuilder.method(upperMethod, null);
            requestBodyText = "";
        }
        detail.setRequestBody(requestBodyText);

        Request request = requestBuilder.build();
        log.info("Sending HTTP {} to {} (httpId={})", upperMethod, url, config.getHttpId());

        try (Response response = client.newCall(request).execute()) {
            Map<String, String> respHeaders = new LinkedHashMap<>();
            response.headers().toMultimap().forEach((k, v) -> {
                if (k != null) {
                    respHeaders.put(k, String.join(",", v));
                }
            });

            String result = handleResponseBody(response);
            detail.setResponseStatus(response.code());
            detail.setResponseHeaders(respHeaders);
            detail.setResponseBody(result);
            detail.setDurationMs(System.currentTimeMillis() - start);
            log.info("HTTP Response: status={}, bytes={}, httpId={}",
                    response.code(), result != null ? result.length() : 0, config.getHttpId());
            return detail;
        } catch (Exception e) {
            log.error("HTTP Request failed: {}", e.getMessage(), e);
            detail.setDurationMs(System.currentTimeMillis() - start);
            throw new JimuNetworkException("HTTP Request failed: " + e.getMessage(), e);
        }
    }

    private OkHttpClient getClient(HttpJimuConfig config) {
        String baseKey = "default";
        OkHttpClient baseClient = defaultClient;
        String poolId = config.getPoolId();
        if (StrUtil.isNotBlank(poolId)) {
            baseKey = "pool:" + poolId;
            baseClient = clientPools.computeIfAbsent(poolId, id -> {
                HttpJimuPool pool = poolService.getById(id);
                if (pool == null) {
                    return defaultClient;
                }

                Dispatcher dispatcher = new Dispatcher();
                dispatcher.setMaxRequests(pool.getMaxRequests() != null ? pool.getMaxRequests() : DEFAULT_MAX_REQUESTS);
                dispatcher.setMaxRequestsPerHost(pool.getMaxRequestsPerHost() != null ? pool.getMaxRequestsPerHost() : DEFAULT_MAX_REQUESTS_PER_HOST);

                int callTimeout = pool.getCallTimeout() != null ? pool.getCallTimeout() : DEFAULT_CALL_TIMEOUT_MS;
                int pingInterval = pool.getPingInterval() != null ? pool.getPingInterval() : DEFAULT_PING_INTERVAL_MS;
                boolean retryOnConnectionFailure = pool.getRetryOnConnectionFailure() == null || pool.getRetryOnConnectionFailure();
                boolean followRedirects = pool.getFollowRedirects() == null || pool.getFollowRedirects();
                boolean followSslRedirects = pool.getFollowSslRedirects() == null || pool.getFollowSslRedirects();

                OkHttpClient.Builder builder = new OkHttpClient.Builder()
                        .dispatcher(dispatcher)
                        .connectionPool(new ConnectionPool(
                                pool.getMaxIdleConnections() != null ? pool.getMaxIdleConnections() : DEFAULT_MAX_IDLE_CONNECTIONS,
                                pool.getKeepAliveDuration() != null ? pool.getKeepAliveDuration() : DEFAULT_KEEP_ALIVE_MS,
                                TimeUnit.MILLISECONDS))
                        .connectTimeout(pool.getConnectTimeout() != null ? pool.getConnectTimeout() : DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .readTimeout(pool.getReadTimeout() != null ? pool.getReadTimeout() : DEFAULT_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .writeTimeout(pool.getWriteTimeout() != null ? pool.getWriteTimeout() : DEFAULT_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .callTimeout(callTimeout, TimeUnit.MILLISECONDS)
                        .retryOnConnectionFailure(retryOnConnectionFailure)
                        .followRedirects(followRedirects)
                        .followSslRedirects(followSslRedirects)
                        .pingInterval(pingInterval, TimeUnit.MILLISECONDS);

                Proxy proxy = resolveProxy(pool.getProxyType(), pool.getProxyHost(), pool.getProxyPort());
                if (proxy != null) {
                    builder.proxy(proxy);
                }
                return builder.build();
            });
        }

        if (!hasConfigOverrides(config)) {
            return baseClient;
        }

        String overrideKey = baseKey + "|" + buildOverrideKey(config);
        OkHttpClient finalBaseClient = baseClient;
        return overrideClients.get(overrideKey, k -> applyConfigOverrides(finalBaseClient, config));
    }

    private String getHeaderIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null || StrUtil.isBlank(name)) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (StrUtil.isBlank(rawQuery)) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            if (StrUtil.isBlank(pair)) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx < 0) {
                result.put(decode(pair), "");
            } else {
                String key = decode(pair.substring(0, idx));
                String value = decode(pair.substring(idx + 1));
                result.put(key, value);
            }
        }
        return result;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean hasConfigOverrides(HttpJimuConfig config) {
        return config.getConnectTimeout() != null
                || config.getReadTimeout() != null
                || config.getWriteTimeout() != null
                || config.getCallTimeout() != null
                || config.getRetryOnConnectionFailure() != null
                || config.getFollowRedirects() != null
                || config.getFollowSslRedirects() != null
                || StrUtil.isNotBlank(config.getProxyHost());
    }

    private OkHttpClient applyConfigOverrides(OkHttpClient baseClient, HttpJimuConfig config) {
        OkHttpClient.Builder builder = baseClient.newBuilder();
        if (config.getConnectTimeout() != null) {
            builder.connectTimeout(config.getConnectTimeout(), TimeUnit.MILLISECONDS);
        }
        if (config.getReadTimeout() != null) {
            builder.readTimeout(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        }
        if (config.getWriteTimeout() != null) {
            builder.writeTimeout(config.getWriteTimeout(), TimeUnit.MILLISECONDS);
        }
        if (config.getCallTimeout() != null) {
            builder.callTimeout(config.getCallTimeout(), TimeUnit.MILLISECONDS);
        }
        if (config.getRetryOnConnectionFailure() != null) {
            builder.retryOnConnectionFailure(config.getRetryOnConnectionFailure());
        }
        if (config.getFollowRedirects() != null) {
            builder.followRedirects(config.getFollowRedirects());
        }
        if (config.getFollowSslRedirects() != null) {
            builder.followSslRedirects(config.getFollowSslRedirects());
        }
        if (StrUtil.isNotBlank(config.getProxyHost())) {
            Proxy proxy = resolveProxy(config.getProxyType(), config.getProxyHost(), config.getProxyPort());
            if (proxy != null) {
                builder.proxy(proxy);
            }
        }
        return builder.build();
    }

    private String buildOverrideKey(HttpJimuConfig config) {
        return String.valueOf(config.getConnectTimeout()) + "|"
                + config.getReadTimeout() + "|"
                + config.getWriteTimeout() + "|"
                + config.getCallTimeout() + "|"
                + config.getRetryOnConnectionFailure() + "|"
                + config.getFollowRedirects() + "|"
                + config.getFollowSslRedirects() + "|"
                + config.getProxyType() + "|"
                + config.getProxyHost() + "|"
                + config.getProxyPort();
    }

    private Proxy resolveProxy(String proxyType, String proxyHost, Integer proxyPort) {
        if (StrUtil.isBlank(proxyHost) || proxyPort == null) {
            return null;
        }
        Proxy.Type type = "SOCKS".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        return new Proxy(type, new InetSocketAddress(proxyHost.trim(), proxyPort));
    }



    private MediaType resolveRawMediaType(String bodyRawType) {
        if (StrUtil.isBlank(bodyRawType)) {
            return null;
        }
        String raw = bodyRawType.trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "text" -> MediaType.parse("text/plain; charset=utf-8");
            case "javascript" -> MediaType.parse("application/javascript; charset=utf-8");
            case "json" -> MediaType.parse("application/json; charset=utf-8");
            case "html" -> MediaType.parse("text/html; charset=utf-8");
            case "xml" -> MediaType.parse("application/xml; charset=utf-8");
            default -> null;
        };
    }

    /**
     * Reads the response body with a 5MB size limit to prevent OOM from large payloads.
     * Binary content (images/videos/audio/archives) is encoded as Base64 string.
     * For truly streaming/large responses the caller should handle separately.
     */
    private String handleResponseBody(Response response) {
        if (response.body() == null) {
            return "";
        }

        MediaType contentType = response.body().contentType();
        String type = contentType != null ? contentType.type() : "text";
        String subtype = contentType != null ? contentType.subtype().toLowerCase(Locale.ROOT) : "plain";

        boolean binary = "image".equals(type)
                || "video".equals(type)
                || "audio".equals(type)
                || ("application".equals(type) && ("octet-stream".equals(subtype)
                || subtype.contains("pdf")
                || subtype.contains("zip")
                || subtype.contains("tar")
                || subtype.contains("gz")));

        final long MAX_BYTES = 5L * 1024 * 1024; // 5MB guard against OOM

        // Fast path: Content-Length header tells us it's too large
        long contentLength = response.body().contentLength();
        if (contentLength > MAX_BYTES) {
            log.warn("Response body too large (Content-Length={}), skipping body read", contentLength);
            return "[Response body too large (" + contentLength + " bytes), read skipped]";
        }

        // Buffer up to MAX_BYTES+1 bytes via Okio source; if more exist, bail out
        try {
            okio.BufferedSource source = response.body().source();
            source.request(MAX_BYTES + 1);
            long buffered = source.getBuffer().size();
            if (buffered > MAX_BYTES) {
                log.warn("Response body too large (buffered={} bytes), skipping body read", buffered);
                return "[Response body too large (>" + MAX_BYTES + " bytes), read skipped]";
            }
            byte[] bytes = source.getBuffer().readByteArray();
            if (binary) {
                return Base64.getEncoder().encodeToString(bytes);
            }
            // Respect charset from Content-Type; fall back to UTF-8
            java.nio.charset.Charset charset = (contentType != null && contentType.charset() != null)
                    ? contentType.charset()
                    : StandardCharsets.UTF_8;
            return new String(bytes, charset);
        } catch (IOException e) {
            log.warn("Failed to read response body: {}", e.getMessage());
            return "";
        }
    }
}
