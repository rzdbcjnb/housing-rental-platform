package com.bulongyu.housing.common;

import java.util.List;

/**
 * 统一分页响应
 */
public record PageResponse<T>(long count, String next, String previous, List<T> results) {
    /**
     * 统一分页响应
     *
     * @param count 数量
     * @param next 下一页链接；无下一页时为 {@code null}
     * @param previous 上一页链接；无上一页时为 {@code null}
     * @param results 处理结果集合
     */
    public PageResponse {
        results = List.copyOf(results);
    }
}
