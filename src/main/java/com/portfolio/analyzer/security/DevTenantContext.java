package com.portfolio.analyzer.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 开发环境租户上下文实现（仅 dev profile 生效）
 * <p>
 * ⚠️ 警告：此实现不安全，仅用于开发环境测试！
 * 从请求头直接读取身份信息，生产环境必须使用 JWT 认证。
 * <p>
 * 支持的请求头：
 * - X-Tenant-Id: 租户 ID（必填）
 * - X-User-Id: 用户 ID（必填）
 * - X-Roles: 角色列表（逗号分隔，可选）
 * - X-Groups: 用户组列表（逗号分隔，可选）
 */
@Component
@Profile("dev")
public class DevTenantContext implements TenantContext {
    
    private static final Logger logger = LoggerFactory.getLogger(DevTenantContext.class);
    
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String GROUPS_HEADER = "X-Groups";
    
    public DevTenantContext() {
        logger.warn("⚠️ DevTenantContext is active - INSECURE, for development only!");
    }
    
    @Override
    public String getTenantId() {
        return current().tenantId();
    }
    
    @Override
    public String getUserId() {
        return current().userId();
    }
    
    @Override
    public Set<String> getRoles() {
        return current().roles();
    }
    
    @Override
    public Set<String> getGroups() {
        return current().groups();
    }
    
    @Override
    public TenantPrincipal current() {
        HttpServletRequest request = getCurrentRequest();
        
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        String userId = request.getHeader(USER_ID_HEADER);
        
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new TenantMissingException("Missing X-Tenant-Id header in dev mode");
        }
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new TenantMissingException("Missing X-User-Id header in dev mode");
        }
        
        Set<String> roles = parseHeaderList(request.getHeader(ROLES_HEADER));
        Set<String> groups = parseHeaderList(request.getHeader(GROUPS_HEADER));
        
        logger.debug("DevTenantContext: tenantId={}, userId={}, roles={}, groups={}", 
            tenantId, userId, roles, groups);
        
        return new TenantPrincipal(tenantId, userId, roles, groups);
    }
    
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes == null) {
            throw new TenantMissingException("No request context available");
        }
        
        return attributes.getRequest();
    }
    
    private Set<String> parseHeaderList(String header) {
        if (header == null || header.trim().isEmpty()) {
            return Set.of();
        }
        
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
