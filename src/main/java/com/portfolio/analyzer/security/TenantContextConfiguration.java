package com.portfolio.analyzer.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 租户上下文配置
 * 
 * <p>确保在非dev profile下，如果没有TenantContext实现，应用启动失败（fail closed）。
 * 
 * @since Stage 2 Refactor
 */
@Configuration
public class TenantContextConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(TenantContextConfiguration.class);
    
    /**
     * 当没有任何TenantContext实现且不是dev profile时，
     * 创建一个失败的bean以阻止应用启动
     */
    @Bean
    @ConditionalOnMissingBean(TenantContext.class)
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dev", matchIfMissing = false)
    public TenantContext failClosedTenantContext() {
        logger.error("╔═══════════════════════════════════════════════════════════╗");
        logger.error("║  ❌  NO TENANT CONTEXT IMPLEMENTATION FOUND  ❌            ║");
        logger.error("╠═══════════════════════════════════════════════════════════╣");
        logger.error("║  Multi-tenancy is required but not configured.            ║");
        logger.error("║                                                           ║");
        logger.error("║  For development: activate 'dev' profile                  ║");
        logger.error("║  For production: provide JWT/OIDC TenantContext impl      ║");
        logger.error("╚═══════════════════════════════════════════════════════════╝");
        
        throw new IllegalStateException(
            "No TenantContext implementation found. " +
            "For development, activate 'dev' profile. " +
            "For production, provide a secure TenantContext implementation (JWT/OIDC)."
        );
    }
}
