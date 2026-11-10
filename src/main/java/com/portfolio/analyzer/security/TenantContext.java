package com.portfolio.analyzer.security;

import java.util.Set;

/**
 * 租户上下文接口
 * <p>
 * 提供当前请求的租户和用户身份信息。
 * 与 AI-RAG 工程师约定的接口，所有实现必须遵循此签名。
 */
public interface TenantContext {
    
    /**
     * 获取租户 ID
     *
     * @return 租户 ID
     * @throws TenantMissingException 如果租户信息缺失
     */
    String getTenantId();
    
    /**
     * 获取用户 ID
     *
     * @return 用户 ID
     * @throws TenantMissingException 如果用户信息缺失
     */
    String getUserId();
    
    /**
     * 获取用户角色集合
     *
     * @return 不可变的角色集合，从不为 null
     */
    Set<String> getRoles();
    
    /**
     * 获取用户组集合
     *
     * @return 不可变的用户组集合，从不为 null
     */
    Set<String> getGroups();
    
    /**
     * 获取完整的租户主体信息
     *
     * @return 不可变的租户主体对象
     * @throws TenantMissingException 如果租户信息缺失
     */
    TenantPrincipal current();
}
