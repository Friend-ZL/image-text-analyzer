package com.portfolio.analyzer.security;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT 租户上下文实现（生产环境）
 * <p>
 * 从 Spring Security 的 JWT 认证信息中提取租户和用户身份。
 * claim 名称可通过配置文件自定义。
 */
@Component
@Profile("!dev")
public class JwtTenantContext implements TenantContext {
    
    private final JwtClaimProperties claimProperties;
    
    public JwtTenantContext(JwtClaimProperties claimProperties) {
        this.claimProperties = claimProperties;
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new TenantMissingException("No authenticated user");
        }
        
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new TenantMissingException("Principal is not a JWT token");
        }
        
        return extractPrincipal(jwt);
    }
    
    private TenantPrincipal extractPrincipal(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(claimProperties.getTenantId());
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new TenantMissingException(
                "Missing tenant claim: " + claimProperties.getTenantId());
        }
        
        String userId = jwt.getSubject();
        if (userId == null || userId.trim().isEmpty()) {
            throw new TenantMissingException("Missing subject (sub) claim");
        }
        
        Set<String> roles = extractRoles(jwt);
        Set<String> groups = extractGroups(jwt);
        
        return new TenantPrincipal(tenantId, userId, roles, groups);
    }
    
    private Set<String> extractRoles(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        
        for (String claimPath : claimProperties.getRoles()) {
            Set<String> claimRoles = extractStringSetFromPath(jwt, claimPath);
            roles.addAll(claimRoles);
        }
        
        return roles;
    }
    
    private Set<String> extractGroups(Jwt jwt) {
        Set<String> groups = new HashSet<>();
        
        for (String claimPath : claimProperties.getGroups()) {
            Set<String> claimGroups = extractStringSetFromPath(jwt, claimPath);
            groups.addAll(claimGroups);
        }
        
        return groups;
    }
    
    /**
     * 从 claim 路径提取字符串集合
     * 支持嵌套路径，如 "realm_access.roles"
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractStringSetFromPath(Jwt jwt, String claimPath) {
        if (claimPath == null || claimPath.isEmpty()) {
            return Set.of();
        }
        
        // 处理嵌套路径，如 "realm_access.roles"
        String[] parts = claimPath.split("\\.");
        Object value = jwt.getClaims();
        
        for (String part : parts) {
            if (value instanceof Map) {
                value = ((Map<String, ?>) value).get(part);
            } else {
                return Set.of();
            }
            
            if (value == null) {
                return Set.of();
            }
        }
        
        // 转换为字符串集合
        return toStringSet(value);
    }
    
    @SuppressWarnings("unchecked")
    private Set<String> toStringSet(Object value) {
        if (value == null) {
            return Set.of();
        }
        
        if (value instanceof Collection) {
            return ((Collection<?>) value).stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }
        
        if (value instanceof String) {
            return Set.of((String) value);
        }
        
        return Set.of();
    }
}
