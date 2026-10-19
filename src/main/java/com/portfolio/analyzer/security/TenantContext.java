package com.portfolio.analyzer.security;

import java.util.List;

/**
 * 租户上下文接口
 * 
 * <p>提供多租户隔离和访问控制所需的身份信息。
 * 生产环境应通过 JWT/OIDC 实现，开发环境可使用请求头模拟。
 * 
 * <p><strong>安全要求：</strong>
 * <ul>
 *   <li>生产环境必须有安全的实现（JWT验证、OIDC）</li>
 *   <li>非dev profile且无实现时应用启动失败（fail closed）</li>
 *   <li>租户ID和用户组不能从请求参数获取，必须从可信凭证解析</li>
 * </ul>
 * 
 * @since Stage 2 Refactor
 */
public interface TenantContext {
    
    /**
     * 获取当前请求的租户ID
     * 
     * @return 租户ID，不能为 null
     * @throws IllegalStateException 如果租户上下文未初始化
     */
    String getTenantId();
    
    /**
     * 获取当前用户ID
     * 
     * @return 用户ID，可选
     */
    String getUserId();
    
    /**
     * 获取当前用户所属的组/角色列表
     * 
     * <p>用于ACL过滤，用户只能访问：
     * <ul>
     *   <li>自己创建的文档（owner_user_id = userId）</li>
     *   <li>授权给其所属组的文档（acl_groups @> groups）</li>
     * </ul>
     * 
     * @return 组列表，不能为 null（可以为空列表）
     */
    List<String> getGroups();
}
