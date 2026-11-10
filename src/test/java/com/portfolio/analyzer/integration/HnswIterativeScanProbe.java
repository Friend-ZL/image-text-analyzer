package com.portfolio.analyzer.integration;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Records {@code hnsw.iterative_scan} as seen on the same connection, inside the
 * transaction that called {@code set_config}. Test-only; wraps the application
 * {@code dataSource} and not Flyway's superuser datasource.
 */
public final class HnswIterativeScanProbe implements BeanPostProcessor {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicReference<String> SEEN = new AtomicReference<>();
    private static final AtomicBoolean INSIDE_TRANSACTION = new AtomicBoolean(false);
    private static final AtomicInteger CONNECTIONS = new AtomicInteger();
    private static final List<String> SQL = new CopyOnWriteArrayList<>();

    public static void reset() {
        SEEN.set(null);
        INSIDE_TRANSACTION.set(false);
        CONNECTIONS.set(0);
        SQL.clear();
    }

    public static String debugSummary() {
        return "connections=" + CONNECTIONS.get() + " sql=" + SQL;
    }

    public static boolean installed() {
        return INSTALLED.get();
    }

    public static String seenValue() {
        return SEEN.get();
    }

    public static boolean wasInsideTransaction() {
        return INSIDE_TRANSACTION.get();
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if ("dataSource".equals(beanName)
                && bean instanceof DataSource dataSource
                && !(bean instanceof ProbingDataSource)) {
            INSTALLED.set(true);
            return new ProbingDataSource(dataSource);
        }
        return bean;
    }

    private static final class ProbingDataSource extends DelegatingDataSource {
        private ProbingDataSource(DataSource delegate) {
            super(delegate);
        }

        @Override
        public Connection getConnection() throws SQLException {
            CONNECTIONS.incrementAndGet();
            return wrapConnection(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return wrapConnection(super.getConnection(username, password));
        }
    }

    private static Connection wrapConnection(Connection raw) {
        return proxy(Connection.class, (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName())
                    && args != null
                    && args.length > 0
                    && args[0] instanceof String sql) {
                SQL.add(sql);
                Statement statement = (Statement) invoke(method, raw, args);
                return wrapStatement(raw, statement, sql);
            }
            // queryForObject(sql, Class) with no bind args uses Statement.executeQuery(sql),
            // which is how hnsw.iterative_scan is set.
            if ("createStatement".equals(method.getName())) {
                Statement statement = (Statement) invoke(method, raw, args);
                return wrapDynamicStatement(raw, statement);
            }
            return invoke(method, raw, args);
        });
    }

    private static Statement wrapStatement(Connection raw, Statement statement, String sql) {
        Class<? extends Statement> type = statement instanceof java.sql.PreparedStatement
                ? java.sql.PreparedStatement.class
                : Statement.class;
        InvocationHandler handler = (proxy, method, args) -> {
            if ("executeQuery".equals(method.getName()) || "execute".equals(method.getName())) {
                Object result = invoke(method, statement, args);
                if (result instanceof ResultSet resultSet) {
                    return wrapResultSet(raw, resultSet, sql);
                }
                return result;
            }
            return invoke(method, statement, args);
        };
        return proxyStatement(type, handler);
    }

    private static Statement wrapDynamicStatement(Connection raw, Statement statement) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (("executeQuery".equals(method.getName()) || "execute".equals(method.getName()))
                    && args != null
                    && args.length > 0
                    && args[0] instanceof String sql) {
                SQL.add(sql);
                Object result = invoke(method, statement, args);
                if (result instanceof ResultSet resultSet) {
                    return wrapResultSet(raw, resultSet, sql);
                }
                return result;
            }
            return invoke(method, statement, args);
        };
        return proxyStatement(Statement.class, handler);
    }

    private static ResultSet wrapResultSet(Connection raw, ResultSet resultSet, String sql) {
        return proxy(ResultSet.class, (proxy, method, args) -> {
            Object result = invoke(method, resultSet, args);
            if ("next".equals(method.getName())
                    && Boolean.TRUE.equals(result)
                    && sql.contains("hnsw.iterative_scan")) {
                // set_config returns the value it applied. Reading the current row
                // does not consume it; the caller still maps the same column.
                SEEN.set(resultSet.getString(1));
                INSIDE_TRANSACTION.set(!raw.getAutoCommit());
            }
            return result;
        });
    }

    private static Statement proxyStatement(Class<? extends Statement> type, InvocationHandler handler) {
        return (Statement) newProxy(type, handler);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) newProxy(type, handler);
    }

    private static Object newProxy(Class<?> type, InvocationHandler handler) {
        ClassLoader loader = type.getClassLoader();
        if (loader == null) {
            loader = HnswIterativeScanProbe.class.getClassLoader();
        }
        return Proxy.newProxyInstance(loader, new Class<?>[]{type}, handler);
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause() == null ? ex : ex.getCause();
        }
    }
}
