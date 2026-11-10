package com.portfolio.analyzer.integration;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

/**
 * TEST-ONLY bootstrap for the {@code app_rw} database role.
 *
 * <p><strong>Delete this class when BE-3's V3 migration lands.</strong>
 * That migration will create {@code app_rw} (LOGIN, not the table owner, not a
 * superuser, {@code NOBYPASSRLS}) and the table/sequence GRANTs. Until then the
 * integration tests create the role themselves so the application datasource is
 * not the container superuser that Flyway uses.
 *
 * <p>The password is generated once per JVM. This class never reads
 * {@code DATABASE_APP_RW_PASSWORD} and does not embed a real secret.
 */
public final class AppRwRoleBootstrap implements Callback {

    static final String ROLE = "app_rw";

    private static volatile String jdbcUrl;
    private static volatile String superuser;
    private static volatile String superuserPassword;

    private AppRwRoleBootstrap() {
    }

    /**
     * Flyway registers this as a callback. The instance itself holds no credentials.
     */
    public static Callback callback() {
        return new AppRwRoleBootstrap();
    }

    static String generatePassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (!password.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new IllegalStateException("generated app_rw password failed the test-only character check");
        }
        return password;
    }

    /**
     * Create {@code app_rw} before the Spring pool connects. No-op when the role
     * already exists (for example once V3 creates it — delete this class then).
     */
    static void ensureRole(PostgreSQLContainer<?> postgres, String password) {
        if (!password.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new IllegalArgumentException("refusing to interpolate an unexpected app_rw password");
        }
        jdbcUrl = postgres.getJdbcUrl();
        superuser = postgres.getUsername();
        superuserPassword = postgres.getPassword();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, superuser, superuserPassword);
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("""
                    DO $do$
                    BEGIN
                      IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_rw') THEN
                        CREATE ROLE app_rw LOGIN PASSWORD '%s'
                          NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
                      END IF;
                    END
                    $do$;
                    """.formatted(password));
            try (ResultSet roles = statement.executeQuery(
                    "SELECT rolsuper, rolbypassrls, rolcanlogin FROM pg_roles WHERE rolname = 'app_rw'")) {
                if (!roles.next()) {
                    throw new IllegalStateException("app_rw was not created");
                }
                if (roles.getBoolean("rolsuper") || roles.getBoolean("rolbypassrls") || !roles.getBoolean("rolcanlogin")) {
                    throw new IllegalStateException(
                            "app_rw must be a LOGIN role, not a superuser, and NOBYPASSRLS");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to ensure app_rw", ex);
        }
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        // GRANTs run on a separate autocommit connection so they commit even if
        // Flyway wraps AFTER_MIGRATE in a transaction it later rolls back.
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        // BE-3: delete this GRANT block together with the rest of the class when V3
        // creates app_rw and grants it DML. It must stay after Flyway, because the
        // tables and sequences do not exist until the migrations have committed.
        if (jdbcUrl == null) {
            throw new IllegalStateException("app_rw bootstrap was not initialized");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, superuser, superuserPassword);
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            String database = connection.getCatalog();
            if (database == null || !database.matches("[A-Za-z0-9_]+")) {
                throw new SQLException("unexpected database name for app_rw grants");
            }
            statement.execute("GRANT CONNECT ON DATABASE " + database + " TO " + ROLE);
            statement.execute("GRANT USAGE ON SCHEMA public TO " + ROLE);
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public TO " + ROLE);
            statement.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO " + ROLE);
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to grant app_rw after Flyway", ex);
        }
    }

    @Override
    public String getCallbackName() {
        return "appRwGrantsAfterFlyway";
    }
}
