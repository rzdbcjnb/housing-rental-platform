package com.bulongyu.housing.entity;

/**
 * 单次 Agent 工具调用的安全执行摘要，不保存原始提示词和敏感参数。
 *
 * @param name 工具名称
 * @param status 执行状态
 * @param durationMs 执行耗时毫秒数
 * @param resultCount 返回结果数量
 */
public record AgentToolTrace(
        String name,
        String status,
        long durationMs,
        int resultCount) {
}
