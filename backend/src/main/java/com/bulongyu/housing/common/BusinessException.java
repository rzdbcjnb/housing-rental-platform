package com.bulongyu.housing.common;

import org.springframework.http.HttpStatus;

/**
 * 通用业务异常
 */
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    /**
     * 初始化 {@code BusinessException} 并注入所需依赖。
     *
     * @param code 业务错误码
     * @param message 消息
     * @param status 状态
     */
    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * 获取业务错误码。
     * @return 业务错误码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取状态。
     * @return 状态
     */
    public HttpStatus getStatus() {
        return status;
    }
}
