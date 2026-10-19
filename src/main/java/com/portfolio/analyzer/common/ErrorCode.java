package com.portfolio.analyzer.common;

import org.springframework.http.HttpStatus;

/**
 * 错误码枚举
 * <p>
 * 错误码格式：E + HTTP状态码 + 两位序号
 * 例如：E40001 表示 400 错误的第 01 号
 */
public enum ErrorCode {
    
    // 成功
    SUCCESS("00000", "成功", HttpStatus.OK),
    
    // 4xx 客户端错误
    INVALID_PARAMETER("E40001", "参数校验失败", HttpStatus.BAD_REQUEST),
    FILE_EMPTY("E40002", "文件不能为空", HttpStatus.BAD_REQUEST),
    QUESTION_EMPTY("E40003", "问题不能为空", HttpStatus.BAD_REQUEST),
    
    FILE_TOO_LARGE("E41301", "文件大小超过限制", HttpStatus.PAYLOAD_TOO_LARGE),
    
    UNSUPPORTED_FILE_TYPE("E41501", "不支持的文件类型", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    
    REQUEST_TIMEOUT("E40801", "请求超时", HttpStatus.REQUEST_TIMEOUT),
    
    TOO_MANY_REQUESTS("E42901", "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    
    // 5xx 服务端错误
    INTERNAL_ERROR("E50001", "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_SERVICE_ERROR("E50002", "AI服务调用失败", HttpStatus.INTERNAL_SERVER_ERROR),
    
    MODEL_TIMEOUT("E50401", "模型调用超时", HttpStatus.GATEWAY_TIMEOUT),
    MODEL_ERROR("E50201", "模型服务异常", HttpStatus.BAD_GATEWAY);
    
    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
    
    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
