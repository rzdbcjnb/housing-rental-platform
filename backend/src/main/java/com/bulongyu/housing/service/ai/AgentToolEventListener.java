package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.AgentToolTrace;

/**
 * Agent 工具执行事件监听器，用于 SSE 状态输出和执行摘要收集。
 */
public interface AgentToolEventListener {
    String CONTEXT_KEY = "toolEventListener";

    AgentToolEventListener NO_OP = new AgentToolEventListener() {
        @Override
        public void onStart(String toolName) {
        }

        @Override
        public void onResult(AgentToolTrace trace) {
        }
    };

    /**
     * 工具开始执行时触发。
     *
     * @param toolName 工具名称
     */
    void onStart(String toolName);

    /**
     * 工具完成或失败时触发。
     *
     * @param trace 工具执行摘要
     */
    void onResult(AgentToolTrace trace);
}
