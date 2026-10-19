package com.portfolio.analyzer.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * TraceId 过滤器
 * <p>
 * 功能：
 * 1. 按优先级获取 traceId：W3C traceparent > X-Request-Id > 新生成
 * 2. 将 traceId 放入 MDC，供日志使用
 * 3. 将 traceId 写入响应头 X-Trace-Id
 * <p>
 * TraceId 格式：32 位小写 hex（与 OpenTelemetry 对齐）
 * <p>
 * 验证规则：
 * - X-Request-Id 不超过 64 字符且只包含合法字符（防日志注入）
 * - W3C traceparent 必须符合格式，trace-id 不能全零
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_RESPONSE_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final SecureRandom RANDOM = new SecureRandom();
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String traceId = extractTraceId(httpRequest);
        
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        httpResponse.setHeader(TRACE_ID_RESPONSE_HEADER, traceId);
        
        try {
            logger.info("Request: {} {} from {}", 
                httpRequest.getMethod(), 
                httpRequest.getRequestURI(), 
                httpRequest.getRemoteAddr());
            
            long startTime = System.currentTimeMillis();
            chain.doFilter(request, response);
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info("Response: status={}, duration={}ms", 
                httpResponse.getStatus(), 
                duration);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
    
    /**
     * 按优先级提取 traceId
     * 1. W3C traceparent 头中的 trace-id
     * 2. X-Request-Id 头
     * 3. 新生成
     */
    private String extractTraceId(HttpServletRequest request) {
        // 优先级 1: W3C traceparent
        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        if (traceparent != null) {
            String traceId = extractTraceIdFromTraceparent(traceparent);
            if (traceId != null) {
                return traceId;
            }
        }
        
        // 优先级 2: X-Request-Id
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        if (isValidRequestId(requestId)) {
            return normalizeToHex32(requestId);
        }
        
        // 优先级 3: 新生成
        return generateTraceId();
    }
    
    /**
     * 从 W3C traceparent 头提取 trace-id
     * 格式: version-trace-id-parent-id-trace-flags
     * 例如: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
     */
    private String extractTraceIdFromTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isEmpty()) {
            return null;
        }
        
        String[] parts = traceparent.split("-");
        if (parts.length < 4) {
            return null;
        }
        
        String version = parts[0];
        String traceId = parts[1];
        
        // 验证 version (00 或 ff 表示无效)
        if (!"00".equals(version)) {
            return null;
        }
        
        // 验证 trace-id: 必须是 32 位 hex 且不能全零
        if (!isValidHex32(traceId) || isAllZeros(traceId)) {
            return null;
        }
        
        return traceId.toLowerCase();
    }
    
    /**
     * 验证 X-Request-Id 是否合法
     * - 不超过 64 字符
     * - 只包含字母数字和常见分隔符（防日志注入）
     */
    private boolean isValidRequestId(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return false;
        }
        
        if (requestId.length() > MAX_REQUEST_ID_LENGTH) {
            return false;
        }
        
        // 只允许字母、数字、连字符、下划线（防注入换行符等）
        return requestId.matches("^[a-zA-Z0-9\\-_]+$");
    }
    
    /**
     * 验证是否为 32 位 hex
     */
    private boolean isValidHex32(String str) {
        return str != null && str.length() == 32 && str.matches("^[0-9a-fA-F]{32}$");
    }
    
    /**
     * 检查是否全零
     */
    private boolean isAllZeros(String str) {
        return str != null && str.matches("^0+$");
    }
    
    /**
     * 将 requestId 标准化为 32 位小写 hex
     * 如果本身就是 32 位 hex，直接使用；否则截断或填充
     */
    private String normalizeToHex32(String requestId) {
        // 移除连字符和下划线
        String normalized = requestId.toLowerCase().replaceAll("[\\-_]", "");
        
        // 如果是 32 位 hex，直接返回
        if (isValidHex32(normalized)) {
            return normalized;
        }
        
        // 否则，只保留 hex 字符并调整到 32 位
        normalized = normalized.replaceAll("[^0-9a-f]", "");
        
        if (normalized.length() >= 32) {
            return normalized.substring(0, 32);
        } else {
            // 不足 32 位，后面补 0
            return String.format("%-32s", normalized).replace(' ', '0');
        }
    }
    
    /**
     * 生成新的 32 位小写 hex trace-id
     */
    private String generateTraceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
