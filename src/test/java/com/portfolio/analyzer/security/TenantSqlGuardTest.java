package com.portfolio.analyzer.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantSqlGuardTest {

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void detectsTenantTableStatementsOnly() {
        assertTrue(TenantGuardingDataSource.referencesTenantTable("SELECT * FROM document"));
        assertTrue(TenantGuardingDataSource.referencesTenantTable("select count(*) from chunk where tenant_id = ?"));
        assertTrue(TenantGuardingDataSource.referencesTenantTable("INSERT INTO public.document (tenant_id) VALUES (?)"));
        assertTrue(TenantGuardingDataSource.referencesTenantTable("UPDATE\n document SET owner_user_id = ?"));
        assertTrue(TenantGuardingDataSource.referencesTenantTable("DELETE FROM chunk WHERE doc_id = ?"));
        assertTrue(TenantGuardingDataSource.referencesTenantTable("SELECT * FROM \"chunk\""));
        assertFalse(TenantGuardingDataSource.referencesTenantTable(
                "SELECT conname FROM pg_constraint WHERE conrelid IN ('chunk'::regclass, 'document'::regclass)"));
        assertFalse(TenantGuardingDataSource.referencesTenantTable(
                "SELECT version FROM flyway_schema_history"));
        assertFalse(TenantGuardingDataSource.referencesTenantTable("SELECT * FROM documentation"));
        assertFalse(TenantGuardingDataSource.referencesTenantTable(null));
        assertFalse(TenantGuardingDataSource.referencesTenantTable(
                "SELECT * FROM pg_class -- FROM document\n WHERE relname = 'x'"));
    }

    @Test
    void tenantSqlOutsideATransactionFails() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TenantGuardingDataSource.requireTransaction("SELECT * FROM document"));
        assertTrue(ex.getMessage().contains("active transaction"));
    }

    @Test
    void tenantSqlInsideATransactionIsAllowed() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertDoesNotThrow(() -> TenantGuardingDataSource.requireTransaction("DELETE FROM chunk WHERE doc_id = ?"));
    }
}
