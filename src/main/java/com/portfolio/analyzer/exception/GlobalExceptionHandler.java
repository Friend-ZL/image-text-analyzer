package com.portfolio.analyzer.exception;

import com.portfolio.analyzer.common.ApiResponse;
import com.portfolio.analyzer.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
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
        
        // 不透传上游错误原文，只返回固定文案
        return buildErrorResponse(ErrorCode.AI_SERVICE_ERROR, ErrorCode.AI_SERVICE_ERROR.getMessage());
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
     * 处理不支持的 HTTP 方法（405）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        logger.error("Method not supported: {}", ex.getMethod());
        String traceId = MDC.get("traceId");
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCode.INVALID_PARAMETER, 
            "不支持的请求方法", 
            traceId
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }
    
    /**
     * 处理资源未找到（404）
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        logger.error("Resource not found: {}", ex.getMessage());
        String traceId = MDC.get("traceId");
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCode.INVALID_PARAMETER, 
            "请求的资源不存在", 
            traceId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
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
     */
    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
            // 检查类名是否包含 Timeout（处理第三方库的超时异常）
            if (current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
    
    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(ErrorCode errorCode, String message) {
        String traceId = MDC.get("traceId");
        ApiResponse<Void> response = ApiResponse.error(errorCode, message, traceId);
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }
}
