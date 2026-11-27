package com.portfolio.analyzer.security;

import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Postgres 上拦截对 {@code document} / {@code chunk} 的 SQL。
 * 没有 Spring 事务时直接失败：事务外的 {@code set_config(..., true)} 不会保留。
 * H2 原样返回连接，不模拟 RLS。
 */
public class TenantGuardingDataSource extends DelegatingDataSource {

    private static final Pattern TENANT_TABLE = Pattern.compile(
            "\\b(?:insert\\s+into|update|delete\\s+from|from|join)\\s+"
                    + "(?:only\\s+)?(?:\"?public\"?\\s*\\.\\s*)?\"?(document|chunk)\"?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private volatile Boolean postgres;

    public TenantGuardingDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    /**
     * 语句是否读写租户表。目录查询里出现的 {@code 'document'::regclass} 不算。
     */
    public static boolean referencesTenantTable(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        String stripped = sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\\r\\n]*", " ");
        return TENANT_TABLE.matcher(stripped).find();
    }

    public static void requireTransaction(String sql) {
        if (!referencesTenantTable(sql)) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Access to tenant table document/chunk requires an active transaction "
                            + "so app.tenant_id can be set with set_config(..., true)");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapIfPostgres(obtainTargetDataSource().getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapIfPostgres(obtainTargetDataSource().getConnection(username, password));
    }

    /**
     * Flyway 用 {@code DataSource.unwrap(DataSource.class)} 复制 URL。
     * 把目标池交出去，避免它读到这层包装。
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        DataSource target = obtainTargetDataSource();
        if (iface.isInstance(target)) {
            return iface.cast(target);
        }
        return super.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(obtainTargetDataSource()) || super.isWrapperFor(iface);
    }

    private Connection wrapIfPostgres(Connection connection) throws SQLException {
        if (!isPostgres(connection)) {
            return connection;
        }
        return guard(connection);
    }

    private boolean isPostgres(Connection connection) throws SQLException {
        Boolean cached = this.postgres;
        if (cached != null) {
            return cached;
        }
        String name = connection.getMetaData().getDatabaseProductName();
        boolean detected = name != null && name.toLowerCase(Locale.ROOT).contains("postgresql");
        this.postgres = detected;
        return detected;
    }

    private static Connection guard(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("unwrap".equals(method.getName()) && args != null && args.length == 1) {
                        Class<?> iface = (Class<?>) args[0];
                        if (iface.isInstance(target)) {
                            return target;
                        }
                        return target.unwrap(iface);
                    }
                    if (args != null && args.length > 0 && args[0] instanceof String sql
                            && (method.getName().startsWith("prepare") || "nativeSQL".equals(method.getName()))) {
                        requireTransaction(sql);
                    }
                    Object result = invoke(target, method, args);
                    if ("createStatement".equals(method.getName()) && result instanceof Statement statement) {
                        return guardStatement(statement);
                    }
                    return result;
                });
    }

    private static Statement guardStatement(Statement target) {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (args != null && args.length > 0 && args[0] instanceof String sql
                            && (name.startsWith("execute") || name.equals("addBatch"))) {
                        requireTransaction(sql);
                    }
                    return invoke(target, method, args);
                });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause != null) {
                throw cause;
            }
            throw ex;
        }
    }
}
