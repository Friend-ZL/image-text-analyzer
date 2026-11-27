package com.portfolio.analyzer.security;

import java.util.Locale;

/**
 * 非 dev/local 下，应用账号不得等于 Flyway owner。
 * 表 owner 即使开了 FORCE RLS 仍可能是超级用户并 BYPASSRLS。
 */
public final class DataSourceRoleRules {

    private DataSourceRoleRules() {
    }

    public static void check(String appUser, String flywayUser, String jdbcUrl) {
        if (jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:h2:")) {
            return;
        }
        if (appUser == null || flywayUser == null || appUser.isBlank() || flywayUser.isBlank()) {
            return;
        }
        if (appUser.equals(flywayUser)) {
            throw new IllegalStateException(
                    "spring.datasource.username must not equal spring.flyway.user "
                            + "(application must connect as app_rw, not the Flyway owner)");
        }
    }
}
