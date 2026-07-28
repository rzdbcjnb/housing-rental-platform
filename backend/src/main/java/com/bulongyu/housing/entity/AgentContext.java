package com.bulongyu.housing.entity;

/**
 * 单轮 Agent 执行所需的服务端可信上下文。
 *
 * @param userId 当前认证用户编号
 * @param conversationId 当前 AI 会话编号
 * @param requestId 请求追踪编号
 */
public record AgentContext(Long userId, Long conversationId, String requestId) {
}
