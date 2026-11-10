package com.portfolio.analyzer.security;

/**
 * 租户信息缺失异常
 * <p>
 * 当请求中缺少必需的租户或用户信息时抛出。
 */
public class TenantMissingException extends RuntimeException {
    
    public TenantMissingException(String message) {
        super(message);
    }
    
    public TenantMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
