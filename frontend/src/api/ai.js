import request from './index'
import { clearAuth, getToken } from '@/utils/auth'

/**
 * 发送AI客服消息（普通模式）
 * @param {string} message - 用户消息
 * @param {number} conversationId - 对话ID（可选）
 * @returns {Promise}
 */
export function sendAiMessageApi(message, conversationId) {
  return request({
    url: '/ai/chat/',
    method: 'post',
    data: { message, conversation_id: conversationId },
    timeout: 120000
  })
}

/**
 * 使用 POST SSE 发送 AI 消息，并逐帧解析服务端具名事件。
 * @param {object} payload - AI 对话请求
 * @param {(event: {event: string, data: any}) => void} onEvent - 事件处理器
 * @param {AbortSignal} signal - 请求取消信号
 * @returns {Promise<void>}
 */
export async function streamAiMessageApi(payload, onEvent, signal) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const response = await fetch(`${baseUrl}/ai/chat/stream/`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`
    },
    body: JSON.stringify(payload),
    signal
  })

  if (!response.ok) {
    if (response.status === 401) {
      clearAuth()
      window.location.href = '/login'
    }
    const errorBody = await response.json().catch(() => ({}))
    throw new Error(errorBody.message || `AI 流式请求失败（${response.status}）`)
  }
  if (!response.body) {
    throw new Error('浏览器未提供可读取的流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    buffer = buffer.replaceAll('\r\n', '\n')
    let frameEnd = buffer.indexOf('\n\n')
    while (frameEnd >= 0) {
      const frame = buffer.slice(0, frameEnd)
      buffer = buffer.slice(frameEnd + 2)
      dispatchSseFrame(frame, onEvent)
      frameEnd = buffer.indexOf('\n\n')
    }
    if (done) {
      if (buffer.trim()) dispatchSseFrame(buffer, onEvent)
      break
    }
  }
}

/**
 * 解析一个 SSE 数据帧，支持多行 data 字段。
 */
function dispatchSseFrame(frame, onEvent) {
  let event = 'message'
  const dataLines = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (dataLines.length === 0) return
  const rawData = dataLines.join('\n')
  let data = rawData
  try {
    data = JSON.parse(rawData)
  } catch {
    // 非 JSON data 仍按文本事件交给调用方处理。
  }
  onEvent?.({ event, data })
}

/**
 * 确认执行一个由 AI 准备的一次性操作。
 */
export function confirmAiActionApi(token, conversationId) {
  return request({
    url: `/ai/actions/${encodeURIComponent(token)}/confirm/`,
    method: 'post',
    data: { conversation_id: conversationId }
  })
}

/**
 * 获取AI对话列表
 * @returns {Promise}
 */
export function getAiConversationsApi() {
  return request({
    url: '/ai/conversations/',
    method: 'get'
  })
}

/**
 * 创建AI对话
 * @param {string} title - 对话标题（可选）
 * @returns {Promise}
 */
export function createAiConversationApi(title = '新对话') {
  return request({
    url: '/ai/conversations/',
    method: 'post',
    data: { title }
  })
}

/**
 * 获取对话消息历史
 * @param {number} conversationId - 对话ID
 * @returns {Promise}
 */
export function getAiConversationMessagesApi(conversationId) {
  return request({
    url: `/ai/conversations/${conversationId}/messages/`,
    method: 'get'
  })
}