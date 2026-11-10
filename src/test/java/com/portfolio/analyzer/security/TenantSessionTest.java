package com.portfolio.analyzer.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantSessionTest {

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void blankTenantIsRejectedBeforeAnySql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantSession session = session(jdbc, "PostgreSQL");

        assertThrows(TenantMissingException.class, () -> session.apply("  "));
        verify(jdbc, never()).queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(String.class),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void postgresWithoutTransactionFails() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantSession session = session(jdbc, "PostgreSQL");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> session.apply("tenant-a"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("active transaction"));
    }

    @Test
    void postgresSetsTransactionLocalGuc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(TenantSession.SET_TENANT_SQL, String.class, "tenant-a")).thenReturn("tenant-a");
        TenantSession session = session(jdbc, "PostgreSQL");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        session.apply("tenant-a");

        verify(jdbc).queryForObject(TenantSession.SET_TENANT_SQL, String.class, "tenant-a");
    }

    @Test
    void h2DoesNotCallSetConfig() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantSession session = session(jdbc, "H2");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        session.apply("tenant-a");

        verify(jdbc, never()).queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(String.class),
                org.mockito.ArgumentMatchers.any());
    }

    private static TenantSession session(JdbcTemplate jdbc, String product) {
        try {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            DatabaseMetaData metaData = mock(DatabaseMetaData.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getDatabaseProductName()).thenReturn(product);
            return new TenantSession(jdbc, dataSource, mock(TenantContext.class));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
