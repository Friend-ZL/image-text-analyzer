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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 开发环境的租户上下文实现
 * 
 * <p>从HTTP请求头读取租户/用户/角色/组信息：
 * <ul>
 *   <li>X-Tenant-Id: 租户ID（必需）</li>
 *   <li>X-User-Id: 用户ID（可选）</li>
 *   <li>X-User-Roles: 逗号分隔的角色列表（API权限，可选）</li>
 *   <li>X-User-Groups: 逗号分隔的组列表（文档ACL，可选）</li>
 * </ul>
 * 
 * <p><strong>仅在dev profile激活。</strong>
 * 启动时会打印警告日志提醒这是开发模式，不应在生产环境使用。
 * 
 * @since Stage 2 - Security refactor
 */
@Component
@Profile("dev")
public class DevTenantContext implements TenantContext {
    
    private static final Logger logger = LoggerFactory.getLogger(DevTenantContext.class);
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_USER_GROUPS = "X-User-Groups";
    
    @PostConstruct
    public void init() {
        logger.warn("========================================");
        logger.warn("DevTenantContext is active (profile=dev)");
        logger.warn("Reading tenant/user/roles/groups from HTTP headers");
        logger.warn("DO NOT USE IN PRODUCTION");
        logger.warn("========================================");
    }
    
    @Override
    public String getTenantId() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            throw new TenantMissingException("No HTTP request in context");
        }
        
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantMissingException("Missing or blank " + HEADER_TENANT_ID + " header");
        }
        
        return tenantId;
    }
    
    @Override
    public String getUserId() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        
        String userId = request.getHeader(HEADER_USER_ID);
        return (userId != null && !userId.isBlank()) ? userId : null;
    }
    
    @Override
    public Set<String> getRoles() {
        return parseHeaderSet(HEADER_USER_ROLES);
    }
    
    @Override
    public Set<String> getGroups() {
        return parseHeaderSet(HEADER_USER_GROUPS);
    }
    
    @Override
    public TenantPrincipal current() {
        return new TenantPrincipal(
                getTenantId(),
                getUserId(),
                getRoles(),
                getGroups()
        );
    }
    
    private Set<String> parseHeaderSet(String headerName) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return Set.of();
        }
        
        String header = request.getHeader(headerName);
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
    
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
