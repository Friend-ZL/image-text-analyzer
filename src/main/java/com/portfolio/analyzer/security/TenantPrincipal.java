package com.portfolio.analyzer.security;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * 租户主体信息（不可变）
 * <p>
 * 包含租户 ID、用户 ID、角色和用户组信息。
 * 所有集合字段都是防御性拷贝的不可变集合。
 *
 * @param tenantId 租户 ID（必填）
 * @param userId   用户 ID（必填）
 * @param roles    角色集合（不可变，不为 null）
 * @param groups   用户组集合（不可变，不为 null）
 */
public record TenantPrincipal(
        String tenantId,
        String userId,
        Set<String> roles,
        Set<String> groups
) {
    
    /**
     * 构造函数：防御性拷贝，确保不可变性
     */
    public TenantPrincipal {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        
        // 防御性拷贝为不可变集合
        roles = roles != null ? Set.copyOf(roles) : Set.of();
        groups = groups != null ? Set.copyOf(groups) : Set.of();
    }
    
    /**
     * 便捷构造方法：不带角色和用户组
     */
    public static TenantPrincipal of(String tenantId, String userId) {
        return new TenantPrincipal(tenantId, userId, Set.of(), Set.of());
    }
}
