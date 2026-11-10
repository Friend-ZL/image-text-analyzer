package com.portfolio.analyzer.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * 在当前事务里执行 {@code SELECT set_config('app.tenant_id', ?, true)}。
 * {@code JdbcVectorStorePort} 和事务监听器共用这一处，避免两套设置。
 * H2 不执行 {@code set_config}。空白租户 ID 直接失败，不把空字符串写进 GUC。
 */
@Component
public class TenantSession {

    private static final Logger logger = LoggerFactory.getLogger(TenantSession.class);

    static final String SET_TENANT_SQL = "SELECT set_config('app.tenant_id', ?, true)";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final TenantContext tenantContext;
    private volatile Boolean postgres;

    public TenantSession(JdbcTemplate jdbcTemplate, DataSource dataSource, TenantContext tenantContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.tenantContext = tenantContext;
    }

    /**
     * 事务真正开始之后调用。没有已解析的租户主体时不写 GUC（RLS 会得到 0 行）。
     * 主体存在但租户 ID 为空白时失败。
     */
    public void onTransactionBegin() {
        if (!isPostgres()) {
            return;
        }
        TenantPrincipal principal;
        try {
            principal = tenantContext.current();
        } catch (TenantMissingException | IllegalStateException ex) {
            logger.debug("No tenant principal at transaction begin: {}", ex.toString());
            return;
        }
        if (principal == null) {
            return;
        }
        apply(principal.tenantId());
    }

    public void apply(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantMissingException("tenant id is required");
        }
        if (!isPostgres()) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "set_config('app.tenant_id') requires an active transaction");
        }
        jdbcTemplate.queryForObject(SET_TENANT_SQL, String.class, tenantId);
        logger.debug("Set transaction-local app.tenant_id");
    }

    private boolean isPostgres() {
        Boolean cached = this.postgres;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.postgres != null) {
                return this.postgres;
            }
            try (Connection connection = dataSource.getConnection()) {
                String name = connection.getMetaData().getDatabaseProductName();
                this.postgres = name != null && name.toLowerCase(Locale.ROOT).contains("postgresql");
            } catch (SQLException ex) {
                throw new IllegalStateException("Cannot detect database product", ex);
            }
            return this.postgres;
        }
    }
}
