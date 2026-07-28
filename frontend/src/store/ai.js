import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  confirmAiActionApi,
  createAiConversationApi,
  getAiConversationMessagesApi,
  getAiConversationsApi,
  streamAiMessageApi
} from '@/api/ai'

function normalizeAction(action) {
  const expired = action.expires_at && new Date(action.expires_at).getTime() <= Date.now()
  return {
    ...action,
    state: action.state || (expired ? 'expired' : 'pending'),
    confirming: false,
    executionResult: action.executionResult || null
  }
}

function normalizeMessage(message) {
  const metadata = message.metadata || {}
  return {
    ...message,
    metadata,
    houses: metadata.houses || message.houses || [],
    sources: metadata.sources || message.sources || [],
    retrievalStatus: metadata.retrieval_status || message.retrieval_status || message.retrievalStatus || null,
    pendingActions: (metadata.pending_actions || message.pendingActions || []).map(normalizeAction),
    toolCalls: metadata.tool_calls || message.toolCalls || [],
    streaming: false
  }
}

export const useAiStore = defineStore('ai', () => {
  const conversations = ref([])
  const currentConversationId = ref(null)
  const messages = ref([])
  const loading = ref(false)
  const sending = ref(false)
  const generationStatus = ref('')
  const activeTools = ref([])
  const lastError = ref('')
  let streamController = null

  async function fetchConversations() {
    try {
      const res = await getAiConversationsApi()
      conversations.value = res || []
      if (!currentConversationId.value && conversations.value.length > 0) {
        await loadMessages(conversations.value[0].id)
      }
    } catch {
    }
  }

  async function loadMessages(conversationId) {
    if (!conversationId) {
      messages.value = []
      currentConversationId.value = null
      return
    }

    loading.value = true
    try {
      const res = await getAiConversationMessagesApi(conversationId)
      messages.value = (res || []).map(normalizeMessage)
      currentConversationId.value = conversationId
    } catch {
    } finally {
      loading.value = false
    }
  }

  async function newConversation() {
    if (sending.value) return false
    try {
      const conversation = await createAiConversationApi()
      currentConversationId.value = conversation.id
      messages.value = []
      conversations.value = [conversation, ...conversations.value]
      return true
    } catch {
      return false
    }
  }

  async function sendMessage(content, houseId = null) {
    const text = String(content || '').trim()
    if (!text || sending.value) return false

    const timestamp = Date.now()
    const userMessage = normalizeMessage({
      id: `temp_user_${timestamp}`,
      role: 'user',
      content: text,
      metadata: {},
      created_at: new Date().toISOString()
    })
    const assistantMessage = normalizeMessage({
      id: `temp_ai_${timestamp}`,
      role: 'assistant',
      content: '',
      metadata: { type: 'text' },
      created_at: new Date().toISOString(),
      streaming: true
    })
    assistantMessage.streaming = true
    messages.value.push(userMessage, assistantMessage)

    sending.value = true
    generationStatus.value = '正在连接'
    activeTools.value = []
    lastError.value = ''
    streamController = new AbortController()
    let completed = false
    try {
      await streamAiMessageApi(
        {
          message: text,
          conversation_id: currentConversationId.value || undefined,
          new_conversation: !currentConversationId.value,
          house_id: houseId || undefined
        },
        ({ event, data }) => {
          switch (event) {
            case 'conversation':
              currentConversationId.value = data.conversation_id
              break
            case 'status':
              generationStatus.value = data.message || ''
              break
            case 'tool_start':
              activeTools.value.push({
                tool: data.tool,
                status: 'running',
                resultCount: null
              })
              break
            case 'tool_result': {
              const tool = [...activeTools.value]
                .reverse()
                .find((item) => item.tool === data.tool && item.status === 'running')
              if (tool) {
                tool.status = data.status
                tool.resultCount = data.result_count
              } else {
                activeTools.value.push({
                  tool: data.tool,
                  status: data.status,
                  resultCount: data.result_count
                })
              }
              assistantMessage.toolCalls = [...activeTools.value]
              break
            }
            case 'delta':
              assistantMessage.content += data.content || ''
              break
            case 'pending_action':
              assistantMessage.pendingActions.push(normalizeAction(data))
              break
            case 'completed':
              completed = true
              assistantMessage.id = data.message_id || assistantMessage.id
              assistantMessage.metadata.type = data.type || 'text'
              assistantMessage.houses = data.houses || []
              assistantMessage.sources = data.sources || []
              assistantMessage.retrievalStatus = data.retrieval_status || null
              assistantMessage.metadata.retrieval_status = assistantMessage.retrievalStatus
              if (data.pending_actions?.length) {
                assistantMessage.pendingActions = data.pending_actions.map(normalizeAction)
              }
              assistantMessage.streaming = false
              generationStatus.value = ''
              break
            case 'error':
              lastError.value = data.message || 'AI 客服处理失败'
              break
          }
        },
        streamController.signal
      )
      if (!completed) {
        throw new Error(lastError.value || 'AI 流式响应未正常完成')
      }
      await fetchConversations()
      return true
    } catch (error) {
      const aborted = error?.name === 'AbortError'
      if (!aborted) {
        lastError.value = error?.message || '发送AI消息失败'
      }
      if (currentConversationId.value) {
        await loadMessages(currentConversationId.value)
      } else {
        messages.value = messages.value.filter(
          (message) => message.id !== userMessage.id && message.id !== assistantMessage.id
        )
      }
      return false
    } finally {
      assistantMessage.streaming = false
      sending.value = false
      generationStatus.value = ''
      streamController = null
    }
  }

  function stopGeneration() {
    streamController?.abort()
  }

  async function confirmAction(action) {
    if (!action || action.confirming || action.state !== 'pending') return null
    action.confirming = true
    try {
      const result = await confirmAiActionApi(action.token, action.conversation_id)
      action.state = 'completed'
      action.executionResult = result
      return result
    } catch (error) {
      action.state = 'failed'
      action.error = error?.response?.data?.message || error?.message || '确认操作失败'
      throw error
    } finally {
      action.confirming = false
    }
  }

  function dismissAction(action) {
    if (action?.state === 'pending') action.state = 'dismissed'
  }

  return {
    conversations,
    currentConversationId,
    messages,
    loading,
    sending,
    generationStatus,
    activeTools,
    lastError,
    fetchConversations,
    loadMessages,
    newConversation,
    sendMessage,
    stopGeneration,
    confirmAction,
    dismissAction
  }
})
