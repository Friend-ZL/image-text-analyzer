package com.portfolio.analyzer.security;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * 不可变的租户主体快照
 * 
 * <p>在controller/entry层从TenantContext解析一次，然后作为显式参数传递给服务层。
 * 防御性拷贝确保Set不可变，支持序列化（用于未来的outbox/async任务表）。
 * 
 * @param tenantId 租户ID（必须）
 * @param userId 用户ID（可选）
 * @param roles 角色集合（API权限，来自OIDC realm/client roles）
 * @param groups 组集合（文档ACL）
 * 
 * @since Stage 2 - Security refactor
 */
public record TenantPrincipal(
        String tenantId,
        String userId,
        Set<String> roles,
        Set<String> groups
) implements Serializable {
    
    /**
     * 规范构造器：防御性拷贝，确保不可变
     */
    public TenantPrincipal {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantMissingException("Tenant ID is required");
        }
        roles = roles != null ? Set.copyOf(roles) : Set.of();
        groups = groups != null ? Set.copyOf(groups) : Set.of();
    }
    
    /**
     * 便利工厂方法：无roles/groups
     */
    public static TenantPrincipal of(String tenantId, String userId) {
        return new TenantPrincipal(tenantId, userId, Set.of(), Set.of());
    }
}
