package com.portfolio.analyzer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.analyzer.common.ApiResponse;
import com.portfolio.analyzer.common.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 自定义认证入口点
 * <p>
 * 当未认证用户访问受保护资源时，返回统一的 401 错误响应体。
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    
    private final ObjectMapper objectMapper;
    
    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        
        String traceId = MDC.get("traceId");
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        
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
