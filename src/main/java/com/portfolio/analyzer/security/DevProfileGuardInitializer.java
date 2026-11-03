package com.portfolio.analyzer.security;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Locale;

/**
 * 在 Bean 创建之前拒绝危险的 dev/local 组合：
 * 与 prod/production 同时激活，或数据源不是本机。
 */
public final class DevProfileGuardInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String BOTH_PROFILES_MESSAGE =
            "Refusing to start: dev/local profile cannot be combined with prod/production";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        boolean insecure = environment.acceptsProfiles(Profiles.of("dev", "local"));
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (insecure && production) {
            throw new IllegalStateException(BOTH_PROFILES_MESSAGE);
        }
        if (!insecure) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url");
        if (!isLocalDatasource(url)) {
            throw new IllegalStateException(
                    "Refusing to start: dev/local profile requires a local datasource "
                            + "(embedded H2, localhost, 127.0.0.1, or ::1). Host was '"
                            + datasourceHost(url) + "'.");
        }
    }

    static boolean isLocalDatasource(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:h2:mem:")
                || lower.startsWith("jdbc:h2:file:")
                || lower.startsWith("jdbc:h2:~/")) {
            return true;
        }
        String host = datasourceHost(url);
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equals("0:0:0:0:0:0:0:1");
    }

    static String datasourceHost(String url) {
        if (url == null) {
            return "unknown";
        }
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return "unknown";
        }
        String rest = url.substring(scheme + 3);
        int at = rest.lastIndexOf('@');
        if (at >= 0) {
            rest = rest.substring(at + 1);
        }
        int end = rest.length();
        int slash = rest.indexOf('/');
        int question = rest.indexOf('?');
        if (slash >= 0) {
            end = Math.min(end, slash);
        }
        if (question >= 0) {
            end = Math.min(end, question);
        }
        String hostPort = rest.substring(0, end);
        if (hostPort.isEmpty()) {
            return "unknown";
        }
        if (hostPort.startsWith("[")) {
            int close = hostPort.indexOf(']');
            if (close > 1) {
                return hostPort.substring(1, close);
            }
            return "unknown";
        }
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            return hostPort.substring(0, colon);
        }
        return hostPort;
    }
}
