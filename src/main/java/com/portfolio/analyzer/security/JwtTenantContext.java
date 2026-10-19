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
        
        for (String claimName : claimProperties.getRoles()) {
            Set<String> claimRoles = extractStringSet(jwt, claimName);
            roles.addAll(claimRoles);
        }
        
        return roles;
    }
    
    private Set<String> extractGroups(Jwt jwt) {
        Set<String> groups = new HashSet<>();
        
        for (String claimName : claimProperties.getGroups()) {
            Set<String> claimGroups = extractStringSet(jwt, claimName);
            groups.addAll(claimGroups);
        }
        
        return groups;
    }
    
    @SuppressWarnings("unchecked")
    private Set<String> extractStringSet(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        
        if (claim == null) {
            return Set.of();
        }
        
        if (claim instanceof Collection) {
            return ((Collection<?>) claim).stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }
        
        if (claim instanceof String) {
            return Set.of((String) claim);
        }
        
        return Set.of();
    }
}
