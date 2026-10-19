package com.portfolio.analyzer.exception;

import com.portfolio.analyzer.common.ApiResponse;
import com.portfolio.analyzer.common.ErrorCode;
import com.portfolio.analyzer.security.TenantMissingException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private String maxFileSize;
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        logger.error("Business exception: {} - {}", ex.getErrorCode().getCode(), ex.getMessage());
        return buildErrorResponse(ex.getErrorCode(), ex.getErrorCode().getMessage());
    }
    
    /**
     * 处理租户信息缺失异常
     */
    @ExceptionHandler(TenantMissingException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantMissing(TenantMissingException ex) {
        logger.error("Tenant missing: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.TENANT_MISSING, ErrorCode.TENANT_MISSING.getMessage());
    }
    
    /**
     * 处理不支持的文件类型
     */
    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFileType(InvalidFileTypeException ex) {
        logger.error("Invalid file type: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.UNSUPPORTED_FILE_TYPE, ErrorCode.UNSUPPORTED_FILE_TYPE.getMessage());
    }
    
    /**
     * 处理文件大小超限
     */
    @ExceptionHandler(FileSizeLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileSizeLimit(FileSizeLimitException ex) {
        logger.error("File size limit exceeded: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getMessage());
    }
    
    /**
     * 处理上传大小超限（Spring 容器级别）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        logger.error("Upload size exceeded: {}", ex.getMessage());
        String message = "文件大小超过" + maxFileSize + "限制";
        return buildErrorResponse(ErrorCode.FILE_TOO_LARGE, message);
    }
    
    /**
     * 处理 IllegalArgumentException - 只返回固定文案，不透传原文
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.error("Invalid argument: {}", ex.getMessage());
        // 不透传原文，只返回固定文案
        return buildErrorResponse(ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.getMessage());
    }
    
    /**
     * 处理 AI 服务异常 - 不透传上游错误，遍历整条异常链检查超时
     */
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleAIServiceException(AIServiceException ex) {
        logger.error("AI service error: {}", ex.getMessage(), ex);
        
        // 遍历整条异常链检查是否为超时异常
        if (isTimeoutException(ex)) {
            return buildErrorResponse(ErrorCode.MODEL_TIMEOUT, ErrorCode.MODEL_TIMEOUT.getMessage());
        }
        
        // 不透传上游错误原文，上游模型错误统一返回 502 / E50201
        return buildErrorResponse(ErrorCode.MODEL_ERROR, ErrorCode.MODEL_ERROR.getMessage());
    }
    
    /**
     * 处理模型超时异常
     */
    @ExceptionHandler(ModelTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleModelTimeout(ModelTimeoutException ex) {
        logger.error("Model timeout: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.MODEL_TIMEOUT, ErrorCode.MODEL_TIMEOUT.getMessage());
    }
    
    /**
     * 处理 Bean Validation 异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        logger.error("Validation failed: {}", message);
        return buildErrorResponse(ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.getMessage());
    }
    
    /**
     * 处理 Constraint Violation 异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining("; "));
        logger.error("Constraint violation: {}", message);
        return buildErrorResponse(ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.getMessage());
    }
    
    /**
     * 处理缺少请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        logger.error("Missing request parameter: {}", ex.getParameterName());
        return buildErrorResponse(ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.getMessage());
    }
    
    /**
     * 处理缺少 multipart 部分
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(MissingServletRequestPartException ex) {
        logger.error("Missing request part: {}", ex.getRequestPartName());
        return buildErrorResponse(ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.getMessage());
    }
    
    /**
     * 处理 JSON 解析失败
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        logger.error("Message not readable: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.getMessage());
    }
    
    /**
     * 处理不支持的 Content-Type
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        logger.error("Media type not supported: {}", ex.getContentType());
        return buildErrorResponse(ErrorCode.UNSUPPORTED_FILE_TYPE, ErrorCode.UNSUPPORTED_FILE_TYPE.getMessage());
    }
    
    /**
     * 处理非 multipart 请求（415）
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException ex) {
        logger.error("Multipart request error: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.UNSUPPORTED_FILE_TYPE, ErrorCode.UNSUPPORTED_FILE_TYPE.getMessage());
    }
    
    /**
     * 处理不支持的 HTTP 方法（405）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        logger.error("Method not supported: {} for {}", ex.getMethod(), ex.getSupportedHttpMethods());
        
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        
        return ResponseEntity
                .status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .headers(headers)
                .body(buildErrorResponse(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage()).getBody());
    }
    
    /**
     * 处理资源未找到（404）
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        logger.error("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }
    
    /**
     * 处理未知异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }
    
    /**
     * 遍历整条异常链，检查是否包含超时异常
     * <p>
     * 只认明确的超时类型：
     * - java.util.concurrent.TimeoutException
     * - java.net.SocketTimeoutException
     * - java.net.http.HttpTimeoutException（含 HttpConnectTimeoutException）
     * - io.netty.handler.timeout.ReadTimeoutException（仅在 classpath 存在时）
     * <p>
     * 防止循环引用，限制最大遍历深度
     */
    private boolean isTimeoutException(Throwable throwable) {
        // Netty ReadTimeoutException（仅在 classpath 存在时检查）
        final boolean hasNettyTimeout = isClassPresent("io.netty.handler.timeout.ReadTimeoutException");
        
        Throwable current = throwable;
        int depth = 0;
        final int MAX_DEPTH = 50; // 防止循环引用
        
        while (current != null && depth < MAX_DEPTH) {
            // 明确的超时类型
            if (current instanceof TimeoutException) {
                return true;
            }
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            
            // java.net.http.HttpTimeoutException（Java 11+）
            String className = current.getClass().getName();
            if ("java.net.http.HttpTimeoutException".equals(className) ||
                "java.net.http.HttpConnectTimeoutException".equals(className)) {
                return true;
            }
            
            // Netty ReadTimeoutException（仅在 classpath 存在时）
            if (hasNettyTimeout && "io.netty.handler.timeout.ReadTimeoutException".equals(className)) {
                return true;
            }
            
            current = current.getCause();
            depth++;
        }
        
        return false;
    }
    
    /**
     * 检查类是否在 classpath 中存在
     */
    private boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(ErrorCode errorCode, String message) {
        String traceId = MDC.get("traceId");
        ApiResponse<Void> response = ApiResponse.error(errorCode, message, traceId);
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }
}
