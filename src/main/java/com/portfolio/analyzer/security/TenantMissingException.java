package com.portfolio.analyzer.security;

/**
 * 租户信息缺失异常
 * 
 * <p>当请求缺少必需的租户标识时抛出。
 * 应映射为HTTP 401 Unauthorized（backend将在BE-1的error-code系统中重新映射）。
 * 
 * @since Stage 2 - Security refactor
 */
public class TenantMissingException extends RuntimeException {
    
    public TenantMissingException(String message) {
        super(message);
    }
    
    public TenantMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
