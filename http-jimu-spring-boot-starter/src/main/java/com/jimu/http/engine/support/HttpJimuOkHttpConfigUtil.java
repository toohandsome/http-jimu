package com.jimu.http.engine.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dns;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FIX (Issue 11): Extracted DNS and Proxy resolution logic from HttpJimuTransportSupport
 * to prevent God-class bloat and clarify responsibilities.
 */
@Slf4j
public class HttpJimuOkHttpConfigUtil {

    public static Dns resolveDns(String dnsOverridesJson) {
        if (StrUtil.isBlank(dnsOverridesJson)) {
            return Dns.SYSTEM;
        }
        Map<String, String> dnsOverrides = parseDnsOverrides(dnsOverridesJson);
        if (dnsOverrides.isEmpty()) {
            return Dns.SYSTEM;
        }
        return hostname -> {
            String mappedIp = dnsOverrides.get(hostname);
            if (StrUtil.isBlank(mappedIp)) {
                return Dns.SYSTEM.lookup(hostname);
            }
            List<InetAddress> resolved = new ArrayList<>(1);
            try {
                resolved.add(InetAddress.getByName(mappedIp));
                return resolved;
            } catch (UnknownHostException e) {
                log.warn("Invalid DNS override ip: host={}, ip={}, fallback system dns", hostname, mappedIp);
                return Dns.SYSTEM.lookup(hostname);
            }
        };
    }

    private static Map<String, String> parseDnsOverrides(String dnsOverridesJson) {
        try {
            Map<?, ?> raw = JSON.parseObject(dnsOverridesJson, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            if (raw == null) {
                return result;
            }
            raw.forEach((k, v) -> {
                if (k == null || v == null) {
                    return;
                }
                String host = String.valueOf(k).trim();
                String ip = String.valueOf(v).trim();
                if (StrUtil.isNotBlank(host) && StrUtil.isNotBlank(ip)) {
                    result.put(host, ip);
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("Invalid dnsOverrides json, fallback system dns: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public static Proxy resolveProxy(String proxyType, String proxyHost, Integer proxyPort) {
        if (StrUtil.isBlank(proxyHost) || proxyPort == null || proxyPort <= 0) {
            return null;
        }
        Proxy.Type type = "SOCKS".equalsIgnoreCase(StrUtil.blankToDefault(proxyType, "HTTP"))
                ? Proxy.Type.SOCKS
                : Proxy.Type.HTTP;
        return new Proxy(type, new InetSocketAddress(proxyHost, proxyPort));
    }
}
