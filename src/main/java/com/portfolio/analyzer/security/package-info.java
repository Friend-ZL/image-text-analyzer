/**
 * 安全和多租户上下文包
 * 
 * <p><strong>注意：此包中的类将由backend的BE-2分支正式拥有。</strong>
 * 
 * <p>PR #2保留这些类的副本以便独立运行，使用约定的签名：
 * <ul>
 *   <li>{@link com.portfolio.analyzer.security.TenantContext}: getTenantId(), getUserId(), 
 *       Set&lt;String&gt; getRoles(), Set&lt;String&gt; getGroups(), TenantPrincipal current()</li>
 *   <li>{@link com.portfolio.analyzer.security.TenantPrincipal}: 不可变record快照
 *       (tenantId, userId, Set&lt;String&gt; roles, Set&lt;String&gt; groups)</li>
 *   <li>{@link com.portfolio.analyzer.security.TenantMissingException}: 租户缺失专用异常</li>
 *   <li>{@link com.portfolio.analyzer.security.DevTenantContext}: dev profile实现</li>
 * </ul>
 * 
 * <p>Rebase时，删除此包并使用BE-2的官方实现。
 * 
 * @since Stage 2 - VectorStore port refactor
 */
package com.portfolio.analyzer.security;
