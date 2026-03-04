package com.example.demo;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/* loaded from: classes4.dex */
@Component
public class AES256Utils {
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_ECB = "AES/ECB/PKCS5Padding";
    private static final String ENCODING = "UTF-8";
    private static final String KEY = "MobileMZkBvVBmT2OA98AebwA8@,.343";

    public  String encrypt(String str) {
        try {
            byte[] bytes = str.getBytes("UTF-8");
            SecretKeySpec secretKeySpec = new SecretKeySpec(KEY.getBytes("UTF-8"), AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_ECB);
            cipher.init(1, secretKeySpec);
//            return Base64.encodeToString(cipher.doFinal(bytes), 0).replaceAll("\n", "");
            return Base64.getEncoder().encodeToString(cipher.doFinal(bytes)).replaceAll("\n", "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public  String decrypt(String str) {
        try {
//            byte[] decode = Base64.decode(str, 0);
            byte[] decode = Base64.getDecoder().decode(str);
            SecretKeySpec secretKeySpec = new SecretKeySpec(KEY.getBytes("UTF-8"), AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_ECB);
            cipher.init(2, secretKeySpec);
            return new String(cipher.doFinal(decode), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    public static void main(String[] strArr) {
//        String decrypt = decrypt("rab2JMeKfO6s1WnIHvCyknPNpPQMg9GmOFSYjVuGkVJFPbLTf7m0YRaxq9ZH3u6y2SdJWcQfHxL6qcfbXZ/6iwrP21hpUlrlETuVjIwNS8f2vdUBqz+vXdlcKhbousEhqjlF243utq+LorMMQoOi4A3ZCZUwnUG9K0nUVXK6yr11ipoF7xBG1Ra5O3OHParf/gKowhAm3N7CMAdzcZZUExSgJhv5SpkVST7K8HlmT6JxRxLr3/YQS9BKGCBWWbAMLkWm2pwIpk4+0Nnxt4IWl0BzO3cM+6L7UgFJ4MiPMiiaJNihBzKrG1DK3GOfLwXjGTPvX4nJnTqhnjsDmE39OfgP38SghlU4Au0eVyCK+zvzm2aqNQ4DiRQ9IRfnT5qP0jqdM4HlgK784um3GjtYB9Lwdcu1p3XMCgfccqTi3By7iILuT3X6UjZKXRGnzAtk7Pf8GY3WlX7+F1kJ8YqN3w7jbWMZI9e1r4lUB0rYY0NBCvIOe0+f6zo+oxTkRQtYx64o0YzEVftJB6yKcN73Tj94eyNnOu4S3JYJT2XE+n74cte6uV4Xi0hXgrJOgIRm+BQls6Li5favDMsItfDVgYITOxTfJVubGam8SOakUFwEFifis/FIKxXGJJtI/FS66Mnz00Z1HjTRClXiI0gnhsPJNIf9sChv34gORRGH84OKu0QpNRjINsdocy+bpRhkwEMvaD/hzrkB0HwXhUAIiH93zNf0XbUSq6d+Xr8CH2In5b5AOMX9KHjtuNRVjDq2adaOt/VWc5jhADl63vf02KTjBQcnf82waR906VkdHETxkRbROZy85YWsp+Gt32rz2ltCP6gcTG3ceGh97VRty54cwtaCEuf6ud/sNvjT4Qq5ZzB8BPrD31T5Umw/XgGKR7aD1vB7pRmYXq+2aDb57kHUSQSwD0axo7O1wFGq6eBVcIYhIt+IwI/rL8WH7hxfVAfQY9jkzC6qS7JtBX6//3dM52dgP00gLhp6fAVAQ9UNN3xoxryMkTi0D5ft67jXdeIEdnfialjWQGyG4rOLBzOBtXDxoukLPYV9QDg06Emb30DwPPsz7sH0V2QX5tVc1baEGSSgyM+ZQtyyHl55r16pyP7X+m39cAGmGHVu79EuoN3hDia5bxeaurX/VtGNd9+zzjseiw48Z+izlZRW/EHUSQSwD0axo7O1wFGq6eBVcIYhIt+IwI/rL8WH7hxfnZ5gKa1EYIKbJgcWAyCUbETw0bZ08wrpSn+MvdwVwe2I40FS4HHOUxQcOb+2UyJNw86643/PSn4ye6HElBqZdzU2BRHpRrOMiMWpvNCdXUSprIOFfNt1hTukeLERT8n07OUKH8zwt0EeZQY5FLHFjxbb01U7ZucVCmxNEv8k6jZ49z7+FOnNJE3PcYr3DTwXWlTAI2CRpL43TFe5um9/4Eqo6JbAPGP9EB/biSYtLBejOzp2tegMkg6ik777Oiwe4KwJGLygsfEDLeFPz/o5kX93zNf0XbUSq6d+Xr8CH2In5b5AOMX9KHjtuNRVjDq2adaOt/VWc5jhADl63vf02KTjBQcnf82waR906VkdHETRbb8Ah5B13q2Pf7aTxl5mbJbZ1LSQgdYxIq8otPVt9u9kpAp5XV5O96Z72D5CtEaW3VYUWSLQROpqOqh4uHDxfbAGbw5df2m0WIXXDswTDVNI98eKOlXW4OlIp5+C4f0D5AhqXfOwUCnCuTZF6CSdWEOV0VtwtnJ+GCXhD4WmrB3dmUTQ28+nc+KVP8gWIwVG/e4JILh0C/XUbjyBvbzfGD+cHOZLubShHO7ioaDfmpCsfvJagCKvE2XHzNAuk3D8VoavAkKjX61NKGzgSnKr0Z7bDopdV0sAIaLsx96eq7BOHpjrCzNz1gbOpxPYK7qknB0nraMpAOS/Q/N6r50z");
//        System.out.println("Decrypted data: " + decrypt);
//        String encrypt = encrypt("2150");
//        System.out.println("encrypt data: " + encrypt);
//    }
}