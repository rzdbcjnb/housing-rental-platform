package com.bulongyu.housing.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP 请求标识与访问日志过滤器
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    public static final String HEADER_NAME = "X-Request-ID";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    /**
     * HTTP 请求标识与访问日志过滤器
     *
     * @param request 请求参数
     * @param response 响应
     * @param filterChain 筛选条件Chain
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId);

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            if (status >= 500) {
                log.error("处理HTTP请求，requestId={}，method={}，path={}，status={}，durationMs={}",
                        requestId, request.getMethod(), request.getRequestURI(), status, durationMs);
            } else if (status >= 400) {
                log.warn("处理HTTP请求，requestId={}，method={}，path={}，status={}，durationMs={}",
                        requestId, request.getMethod(), request.getRequestURI(), status, durationMs);
            } else {
                log.info("处理HTTP请求，requestId={}，method={}，path={}，status={}，durationMs={}",
                        requestId, request.getMethod(), request.getRequestURI(), status, durationMs);
            }
        }
    }
}
