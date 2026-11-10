package com.portfolio.analyzer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.analyzer.common.ApiResponse;
import com.portfolio.analyzer.common.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 自定义访问拒绝处理器
 * <p>
 * 当已认证用户访问无权限资源时，返回统一的 403 错误响应体。
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    
    private final ObjectMapper objectMapper;
    
    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        
        String traceId = MDC.get("traceId");
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        
        ApiResponse<Void> apiResponse = ApiResponse.error(
            errorCode,
            errorCode.getMessage(),
            traceId
        );
        
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        objectMapper.writeValue(response.getWriter(), apiResponse);
    }
}
