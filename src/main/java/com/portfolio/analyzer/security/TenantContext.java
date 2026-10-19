package com.portfolio.analyzer.security;

import java.util.Set;

/**
 * 租户上下文接口
 * 
 * <p>定义从请求上下文（HTTP头、JWT等）提取租户、用户、角色和组信息的契约。
 * 
 * <p>使用方式：在controller/entry层调用 {@link #current()} 一次获取不可变快照，
 * 然后将 {@link TenantPrincipal} 作为显式参数传递给服务层，而不是在服务内部调用此接口。
 * 
 * @since Stage 2 - Security refactor
 */
public interface TenantContext {
    
    /**
     * 获取当前租户ID
     * @return 租户ID
     * @throws TenantMissingException 如果租户ID缺失或空白
     */
    String getTenantId();
    
    /**
     * 获取当前用户ID
     * @return 用户ID，可能为null（匿名访问）
     */
    String getUserId();
    
    /**
     * 获取当前用户的角色集合（API权限）
     * 
     * <p>来自OIDC realm/client roles，用于API端点访问控制。
     * 
     * @return 角色集合，不为null（但可能为空）
     */
    Set<String> getRoles();
    
    /**
     * 获取当前用户的组集合（文档ACL）
     * 
     * <p>用于文档级访问控制。
     * 
     * @return 组集合，不为null（但可能为空）
     */
    Set<String> getGroups();
    
    /**
     * 获取当前租户主体的不可变快照
     * 
     * <p>应在controller/entry层调用一次，然后将返回的 {@link TenantPrincipal}
     * 作为显式参数传递给服务层方法。
     * 
     * @return 租户主体快照
     * @throws TenantMissingException 如果租户ID缺失或空白
     */
    TenantPrincipal current();
}
