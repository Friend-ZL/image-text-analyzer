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
import java.util.UUID;

/**
 * TraceId 过滤器
 * <p>
 * 功能：
 * 1. 从请求头 X-Request-Id 获取 traceId，如果没有则生成新的
 * 2. 将 traceId 放入 MDC，供日志使用
 * 3. 将 traceId 写入响应头 X-Trace-Id
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_RESPONSE_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = generateTraceId();
        }
        
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
    
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
