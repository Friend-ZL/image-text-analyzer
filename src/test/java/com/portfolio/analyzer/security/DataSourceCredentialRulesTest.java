package com.portfolio.analyzer.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceCredentialRulesTest {

    @Test
    void rejectsBlankAndQuotedAppRwPassword() {
        IllegalStateException blank = assertThrows(IllegalStateException.class, () -> AppRwPasswordRules.check("  "));
        assertTrue(blank.getMessage().contains("DATABASE_APP_RW_PASSWORD"));
        IllegalStateException quoted = assertThrows(IllegalStateException.class,
                () -> AppRwPasswordRules.check("ab'cd"));
        assertTrue(quoted.getMessage().contains("single quote"));
        AppRwPasswordRules.check("change_me");
    }

    @Test
    void rejectsApplicationUserEqualToFlywayOwnerOnPostgres() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                DataSourceRoleRules.check("analyzer", "analyzer", "jdbc:postgresql://localhost:5432/analyzer_db"));
        assertTrue(ex.getMessage().contains("app_rw"));
    }

    @Test
    void allowsSplitRolesAndSkipsH2() {
        DataSourceRoleRules.check("app_rw", "analyzer", "jdbc:postgresql://localhost:5432/analyzer_db");
        DataSourceRoleRules.check("sa", "sa", "jdbc:h2:mem:testdb");
    }

    @Test
    void clockSkewAcceptsSecondsAndDurationAndRejectsNegative() {
        assertEquals(Duration.ofSeconds(60), JwtResourceServerProperties.parseClockSkew("60"));
        assertEquals(Duration.ofSeconds(60), JwtResourceServerProperties.parseClockSkew("60s"));
        assertEquals(Duration.ofSeconds(0), JwtResourceServerProperties.parseClockSkew("0"));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> JwtResourceServerProperties.parseClockSkew("-1"));
        assertTrue(ex.getMessage().contains("clock-skew"));
        assertThrows(IllegalStateException.class, () -> JwtResourceServerProperties.parseClockSkew("-5s"));
    }
}
