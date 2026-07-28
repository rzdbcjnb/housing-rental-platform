package com.bulongyu.housing.handler;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.common.ErrorResponse;
import com.bulongyu.housing.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 全局异常处理器
     *
     * @param exception 异常对象
     * @param request 请求参数
     */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException exception, HttpServletRequest request) {
        log.warn("处理业务异常，requestId={}，method={}，path={}，status={}，code={}",
                requestId(request), request.getMethod(), request.getRequestURI(),
                exception.getStatus().value(), exception.getCode());
        return response(exception.getMessage(), exception.getCode(), exception.getStatus(), request);
    }

    /**
     * 全局异常处理器
     *
     * @param exception 异常对象
     * @param request 请求参数
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                    HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid request" : error.getDefaultMessage())
                .orElse("Invalid request");
        log.warn("处理参数校验失败，requestId={}，method={}，path={}",
                requestId(request), request.getMethod(), request.getRequestURI());
        return response(message, "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 将请求参数类型错误、缺少参数和 JSON 解析错误统一转换为 400 响应。
     *
     * @param exception 异常对象
     * @param request 请求参数
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception,
                                                       HttpServletRequest request) {
        log.warn("处理请求格式错误，requestId={}，method={}，path={}，type={}",
                requestId(request), request.getMethod(), request.getRequestURI(),
                exception.getClass().getSimpleName());

        return response("请求参数格式错误", "INVALID_REQUEST", HttpStatus.BAD_REQUEST, request);
    }
    /**
     * 全局异常处理器
     *
     * @param exception 异常对象
     * @param request 请求参数
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException exception,
                                                  HttpServletRequest request) {
        log.info("处理资源不存在，requestId={}，method={}，path={}",
                requestId(request), request.getMethod(), request.getRequestURI());
        return response("Resource not found", "NOT_FOUND", HttpStatus.NOT_FOUND, request);
    }

    /**
     * 全局异常处理器
     *
     * @param exception 异常对象
     * @param request 请求参数
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("处理未预期异常，requestId={}，method={}，path={}",
                requestId(request), request.getMethod(), request.getRequestURI(), exception);
        return response("Internal server error", "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * 全局异常处理器
     *
     * @param detail 详情
     * @param code 业务错误码
     * @param status 状态
     * @param request 请求参数
     */
    private ResponseEntity<ErrorResponse> response(String detail, String code, HttpStatus status,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(detail, code, requestId(request), Instant.now()));
    }

    /**
     * 全局异常处理器
     *
     * @param request 请求参数
     */
    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
