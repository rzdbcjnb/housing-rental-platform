package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.AiConversationContext;
import com.bulongyu.housing.entity.AiMessage;
import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.entity.SearchConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从可信请求和历史消息中恢复 AI 会话业务状态，并生成下一轮状态快照。
 */
@Service
public class AiConversationContextService {
    private static final Logger log = LoggerFactory.getLogger(AiConversationContextService.class);
    private static final String CONTEXT_METADATA_KEY = "conversation_context";
    private static final Pattern HOUSE_ID_PATTERN = Pattern.compile(
            "(?:房源(?:编号|ID)?[:：#]?\\s*(\\d+))|(?:(\\d+)\\s*号房源)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDINAL_PATTERN = Pattern.compile(
            "(?:第)?([一二三四五六七八九十]|\\d+)(?:套|个房源|套房源)");

    private final ObjectMapper json;
    private final IntentService intentService;

    /**
     * 初始化会话上下文服务。
     *
     * @param json JSON 序列化组件
     * @param intentService 确定性意图和约束解析服务
     */
    public AiConversationContextService(ObjectMapper json, IntentService intentService) {
        this.json = json;
        this.intentService = intentService;
    }

    /**
     * 恢复上一轮状态，并使用当前消息中的可信信息覆盖旧状态。
     * 房源编号优先级为：当前文本明确编号、当前房源卡片、候选序号、历史当前房源。
     *
     * @param recentMessages 按时间正序排列的最近消息
     * @param selectedHouseId 前端当前选择的房源编号
     * @param query 当前用户问题
     * @return 本轮调用模型前可使用的结构化状态
     */
    public AiConversationContext resolve(List<AiMessage> recentMessages,
                                         Long selectedHouseId,
                                         String query) {
        AiConversationContext previous = restoreLatestSnapshot(recentMessages);
        Long currentHouseId = explicitHouseId(query);
        if (currentHouseId == null && validHouseId(selectedHouseId)) {
            currentHouseId = selectedHouseId;
        }
        if (currentHouseId == null) {
            currentHouseId = candidateReference(query, previous.candidateHouseIds());
        }
        if (currentHouseId == null) {
            currentHouseId = previous.currentHouseId();
        }
        if (currentHouseId == null) {
            currentHouseId = latestExplicitHouseId(recentMessages);
        }

        IntentResult deterministic = intentService.rules(query);
        List<SearchConstraint> constraints = deterministic.constraints().isEmpty()
                ? previous.searchConstraints()
                : deterministic.constraints();
        String lastIntent = deterministic.intent() == IntentResult.Intent.GENERAL_CHAT
                ? previous.lastIntent()
                : deterministic.intent().name();
        if (deterministic.intent() == IntentResult.Intent.HOUSE_RECOMMEND) {
            currentHouseId = null;
        }

        AiConversationContext resolved = new AiConversationContext(
                currentHouseId,
                previous.candidateHouseIds(),
                constraints,
                lastIntent);
        log.info("恢复AI会话上下文，参数：currentHouseId={}，candidateCount={}，constraintCount={}",
                resolved.currentHouseId(), resolved.candidateHouseIds().size(), resolved.searchConstraints().size());
        return resolved;
    }

    /**
     * 根据本轮可信返回数据更新候选房源，并生成要写入消息元数据的状态快照。
     *
     * @param current 本轮调用模型前的状态
     * @param result 编排层处理结果
     * @return 完成本轮后的状态
     */
    public AiConversationContext afterResponse(AiConversationContext current,
                                               AiOrchestrator.Result result) {
        boolean houseSearchOutcome = result.retrievalStatus() != null;
        List<Long> candidateIds = houseSearchOutcome
                ? result.houses().stream().map(house -> house.id()).toList()
                : result.houses().isEmpty()
                        ? current.candidateHouseIds()
                        : result.houses().stream().map(house -> house.id()).toList();
        Long currentHouseId = houseSearchOutcome || "house_list".equals(result.type())
                ? null
                : current.currentHouseId();
        return new AiConversationContext(
                currentHouseId,
                candidateIds,
                current.searchConstraints(),
                current.lastIntent());
    }
    /**
     * 返回消息元数据中使用的固定上下文键。
     */
    public String metadataKey() {
        return CONTEXT_METADATA_KEY;
    }

    /**
     * 从最近一条包含快照的助手消息中恢复状态。
     */
    private AiConversationContext restoreLatestSnapshot(List<AiMessage> recentMessages) {
        if (recentMessages == null) {
            return AiConversationContext.empty();
        }
        for (int index = recentMessages.size() - 1; index >= 0; index--) {
            AiMessage message = recentMessages.get(index);
            if (message == null || message.metadata() == null || message.metadata().isBlank()) {
                continue;
            }
            try {
                Map<String, Object> metadata = json.readValue(message.metadata(), new TypeReference<>() {});
                Object snapshot = metadata.get(CONTEXT_METADATA_KEY);
                if (snapshot != null) {
                    return json.convertValue(snapshot, AiConversationContext.class);
                }
            }
            catch (RuntimeException exception) {
                log.warn("恢复AI会话上下文失败，忽略损坏快照，参数：messageId={}，exceptionType={}",
                        message.id(), exception.getClass().getSimpleName());
            }
        }
        return AiConversationContext.empty();
    }

    /**
     * 为尚未写入上下文快照的旧会话恢复最近一次明确房源编号。
     */
    private Long latestExplicitHouseId(List<AiMessage> recentMessages) {
        if (recentMessages == null) {
            return null;
        }
        for (int index = recentMessages.size() - 1; index >= 0; index--) {
            AiMessage message = recentMessages.get(index);
            if (message != null && "user".equalsIgnoreCase(message.role())) {
                Long houseId = explicitHouseId(message.content());
                if (houseId != null) {
                    return houseId;
                }
            }
        }
        return null;
    }

    /**
     * 解析“房源151”或“151号房源”中的明确房源编号。
     */
    private Long explicitHouseId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = HOUSE_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        try {
            long houseId = Long.parseLong(value);
            return validHouseId(houseId) ? houseId : null;
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 将“第一套”解析为最近推荐候选中的对应房源。
     */
    private Long candidateReference(String query, List<Long> candidateIds) {
        if (query == null || candidateIds == null || candidateIds.isEmpty()) {
            return null;
        }
        Matcher matcher = ORDINAL_PATTERN.matcher(query);
        if (!matcher.find()) {
            return null;
        }
        int ordinal = parseOrdinal(matcher.group(1));
        return ordinal > 0 && ordinal <= candidateIds.size() ? candidateIds.get(ordinal - 1) : null;
    }

    /**
     * 将中文或阿拉伯数字序号转换为整数。
     */
    private int parseOrdinal(String value) {
        if (value.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(value);
        }
        return switch (value) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> -1;
        };
    }

    private boolean validHouseId(Long houseId) {
        return houseId != null && houseId > 0;
    }
}