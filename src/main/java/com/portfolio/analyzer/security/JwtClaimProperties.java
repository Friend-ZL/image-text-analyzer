package com.portfolio.analyzer.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * JWT claim 映射配置
 * <p>
 * 定义如何从 JWT claims 中提取租户、用户、角色和用户组信息。
 */
@Configuration
@ConfigurationProperties(prefix = "app.security.jwt.claims")
public class JwtClaimProperties {
    
    /**
     * 租户 ID claim 名称
     */
    private String tenantId = "tenant_id";
    
    /**
     * 固定角色路径，取并集。
     * {@code resource_access.<client-id>.roles} 不写在这里，client-id 来自
     * {@code app.security.jwt.client-id}，由 {@link JwtTenantContext} 追加。
     */
    private List<String> roles = List.of(
        "realm_access.roles",
        "roles"
    );
    
    /**
     * 用户组 claim 名称列表
     */
    private List<String> groups = List.of(
        "groups"
    );
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public List<String> getRoles() {
        return roles;
    }
    
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
    
    public List<String> getGroups() {
        return groups;
    }
    
    public void setGroups(List<String> groups) {
        this.groups = groups;
    }
}
