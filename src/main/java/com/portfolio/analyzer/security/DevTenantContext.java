package com.portfolio.analyzer.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 开发环境租户上下文实现（仅供开发测试使用）
 * 
 * <p><strong>⚠️ 安全警告：此实现不安全，仅用于开发环境！</strong></p>
 * 
 * <p>从HTTP请求头读取租户信息：
 * <ul>
 *   <li>X-Tenant-Id: 租户ID</li>
 *   <li>X-User-Id: 用户ID</li>
 *   <li>X-User-Groups: 用户组（逗号分隔）</li>
 * </ul>
 * 
 * <p>生产环境必须使用基于JWT/OIDC的安全实现。
 * 
 * @since Stage 2 Refactor
 */
@Profile("dev")
@Component
public class DevTenantContext implements TenantContext {
    
    private static final Logger logger = LoggerFactory.getLogger(DevTenantContext.class);
    
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_GROUPS = "X-User-Groups";
    
    @PostConstruct
    public void init() {
        logger.warn("╔═══════════════════════════════════════════════════════════╗");
        logger.warn("║  ⚠️  DEV TENANT CONTEXT ACTIVE - NOT FOR PRODUCTION  ⚠️   ║");
        logger.warn("╠═══════════════════════════════════════════════════════════╣");
        logger.warn("║  Tenant info is read from request headers:                ║");
        logger.warn("║  - X-Tenant-Id                                            ║");
        logger.warn("║  - X-User-Id                                              ║");
        logger.warn("║  - X-User-Groups (comma-separated)                        ║");
        logger.warn("║                                                           ║");
        logger.warn("║  Production MUST use JWT/OIDC-based implementation!       ║");
        logger.warn("╚═══════════════════════════════════════════════════════════╝");
    }
    
    @Override
    public String getTenantId() {
        HttpServletRequest request = getCurrentRequest();
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalStateException("Tenant ID is required. Set " + HEADER_TENANT_ID + " header.");
        }
        return tenantId.trim();
    }
    
    @Override
    public String getUserId() {
        HttpServletRequest request = getCurrentRequest();
        String userId = request.getHeader(HEADER_USER_ID);
        return userId != null ? userId.trim() : null;
    }
    
    @Override
    public List<String> getGroups() {
        HttpServletRequest request = getCurrentRequest();
        String groupsHeader = request.getHeader(HEADER_USER_GROUPS);
        if (groupsHeader == null || groupsHeader.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(groupsHeader.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
    
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("No request context available");
        }
        return attrs.getRequest();
    }
}
