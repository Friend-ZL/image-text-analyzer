package com.portfolio.analyzer.security;

import com.portfolio.analyzer.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 已认证请求必须能解析出租户。缺失时返回 E40102，而不是进入业务代码。
 * 只挂在 JWT 过滤链上，不作为独立 Servlet Filter 注册。
 */
final class TenantEnforcementFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    TenantEnforcementFilter(TenantContext tenantContext,
                            CustomAuthenticationEntryPoint authenticationEntryPoint) {
        this.tenantContext = tenantContext;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            tenantContext.current();
        } catch (TenantMissingException ex) {
            authenticationEntryPoint.write(response, ErrorCode.TENANT_MISSING);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
