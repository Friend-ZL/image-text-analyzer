package com.portfolio.analyzer.security;

/**
 * Flyway 把 {@code DATABASE_APP_RW_PASSWORD} 替换进 V3 的 {@code PASSWORD '...'}。
 * 单引号会打断 SQL，空值会把角色密码设成空。
 */
public final class AppRwPasswordRules {

    private AppRwPasswordRules() {
    }

    public static void check(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "DATABASE_APP_RW_PASSWORD must be set to a non-blank value when Flyway is enabled");
        }
        if (password.indexOf('\'') >= 0 || password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            throw new IllegalStateException(
                    "DATABASE_APP_RW_PASSWORD must not contain a single quote or line break");
        }
    }
}
