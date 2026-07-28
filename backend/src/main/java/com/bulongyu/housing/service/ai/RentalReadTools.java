package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.dto.HouseSearchToolRequest;
import com.bulongyu.housing.entity.AgentToolTrace;
import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.entity.SearchConstraint;
import com.bulongyu.housing.service.HouseService;
import com.bulongyu.housing.vo.AiHouseView;
import com.bulongyu.housing.vo.HouseDetailView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import static com.bulongyu.housing.entity.SearchConstraint.Field.BATHROOMS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.BEDROOMS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.KITCHENS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.LIVING_ROOMS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.PRICE;
import static com.bulongyu.housing.entity.SearchConstraint.Field.REGION;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.AROUND;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.CONTAINS;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.EQ;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.GTE;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.LTE;
import static com.bulongyu.housing.entity.SearchConstraint.Strength.HARD;
import static com.bulongyu.housing.entity.SearchConstraint.Strength.SOFT;

/**
 * 租房 Agent 可调用的只读业务工具。
 */
@Component
public class RentalReadTools {
    public static final String USER_ID_CONTEXT_KEY = "userId";
    public static final String CONVERSATION_ID_CONTEXT_KEY = "conversationId";
    public static final String REQUEST_ID_CONTEXT_KEY = "requestId";
    public static final String TOOL_CALL_COUNTER_CONTEXT_KEY = "toolCallCounter";
    public static final String TOOL_CALL_LIMIT_CONTEXT_KEY = "toolCallLimit";

    private static final Logger log = LoggerFactory.getLogger(RentalReadTools.class);
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 20;
    private static final int MAX_QUERY_LENGTH = 500;
    private static final int MAX_REGION_LENGTH = 50;
    private static final int MAX_ROOM_COUNT = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final AiHouseSearchService houseSearchService;
    private final HouseService houseService;
    private final KnowledgeRagService knowledgeRagService;

    /**
     * 初始化租房 Agent 只读工具。
     *
     * @param houseSearchService AI 房源统一检索服务
     * @param houseService 房源业务服务
     * @param knowledgeRagService 租房知识检索服务
     */
    public RentalReadTools(AiHouseSearchService houseSearchService,
                           HouseService houseService,
                           KnowledgeRagService knowledgeRagService) {
        this.houseSearchService = houseSearchService;
        this.houseService = houseService;
        this.knowledgeRagService = knowledgeRagService;
    }

    /**
     * 根据地区、价格和户型约束检索已审核且上架的房源。
     *
     * @param request 结构化房源搜索参数
     * @param toolContext 服务端注入的工具调用上下文
     * @return 检索状态和最多二十条公开房源摘要
     */
    @Tool(name = "searchHouses", description = "根据地区、预算和户型条件搜索公开房源，并返回明确检索状态。只用于检索，不执行收藏或发送消息。")
    public HouseSearchToolResult searchHouses(
            @ToolParam(description = "结构化房源搜索条件") HouseSearchToolRequest request,
            ToolContext toolContext) {
        return executeTool(toolContext, "searchHouses", userId -> {
            IntentResult intentResult = buildIntent(request);
            int resultLimit = normalizeLimit(request.limit());
            AiHouseSearchService.SearchResult result = houseSearchService.search(intentResult, resultLimit);
            List<AiHouseView> houses = result.houses().stream()
                    .map(AiHouseView::from)
                    .toList();
            return new HouseSearchToolResult(result.status().name(), houses);
        }, result -> result.houses().size());
    }
    /**
     * 查询单套公开房源的安全详情，不向模型暴露房东手机号和详细地址。
     *
     * @param houseId 房源编号
     * @param toolContext 服务端注入的工具调用上下文
     * @return 可提供给模型的公开房源详情
     */
    @Tool(name = "getHouseDetail", description = "查询一套公开房源的价格、面积、户型、区域和描述。")
    public HouseToolDetail getHouseDetail(
            @ToolParam(description = "要查询的房源编号") Long houseId,
            ToolContext toolContext) {
        return executeTool(
                toolContext,
                "getHouseDetail",
                userId -> safeDetail(requireHouseId(houseId), userId),
                result -> 1);
    }

    /**
     * 按统一字段读取并比较二至五套公开房源。
     *
     * @param houseIds 需要比较的房源编号
     * @param toolContext 服务端注入的工具调用上下文
     * @return 保持请求顺序的房源比较项
     */
    @Tool(name = "compareHouses", description = "比较二至五套公开房源的价格、面积、户型和区域。")
    public List<HouseComparisonItem> compareHouses(
            @ToolParam(description = "二至五个不重复的房源编号") List<Long> houseIds,
            ToolContext toolContext) {
        return executeTool(toolContext, "compareHouses", userId -> {
            List<Long> normalizedHouseIds = normalizeComparisonIds(houseIds);
            return normalizedHouseIds.stream()
                    .map(houseId -> comparisonItem(safeDetail(houseId, userId)))
                    .toList();
        }, List::size);
    }

    /**
     * 检索租房 FAQ 片段和来源，不在工具内部再次调用大模型。
     *
     * @param query 租房知识问题
     * @param toolContext 服务端注入的工具调用上下文
     * @return FAQ 检索结果和来源
     */
    @Tool(name = "searchKnowledge", description = "检索押金、合同、退租和维修等租房 FAQ，并返回来源。")
    public KnowledgeRagService.SearchResult searchKnowledge(
            @ToolParam(description = "需要查询的租房知识问题") String query,
            ToolContext toolContext) {
        return executeTool(toolContext, "searchKnowledge", userId -> {
            String normalizedQuery = normalizeRequiredText(query, "query", MAX_QUERY_LENGTH);
            return knowledgeRagService.search(normalizedQuery);
        }, result -> result.sources().size());
    }

    /**
     * 在统一边界内校验调用预算、执行工具并记录状态、耗时和结果数量。
     */
    private <T> T executeTool(ToolContext toolContext,
                              String toolName,
                              Function<Long, T> action,
                              ToIntFunction<T> resultCounter) {
        Long userId = requireUserId(toolContext);
        int invocation = reserveToolCall(toolContext);
        AgentToolEventListener listener = listener(toolContext);
        long startedAt = System.nanoTime();
        listener.onStart(toolName);
        try {
            T result = action.apply(userId);
            long durationMs = elapsedMillis(startedAt);
            int resultCount = resultCounter.applyAsInt(result);
            listener.onResult(new AgentToolTrace(
                    toolName, "success", durationMs, resultCount));
            log.info("完成AI只读工具调用，参数：tool={}，status=success，userId={}，invocation={}，durationMs={}，resultCount={}",
                    toolName, userId, invocation, durationMs, resultCount);
            return result;
        }
        catch (RuntimeException exception) {
            long durationMs = elapsedMillis(startedAt);
            listener.onResult(new AgentToolTrace(
                    toolName, "failed", durationMs, 0));
            log.warn("AI只读工具调用失败，参数：tool={}，status=failed，userId={}，invocation={}，durationMs={}，exceptionType={}",
                    toolName,
                    userId,
                    invocation,
                    durationMs,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }
    /**
     * 读取本轮 Agent 注入的工具事件监听器。
     */
    private AgentToolEventListener listener(ToolContext toolContext) {
        Object value = toolContext.getContext().get(AgentToolEventListener.CONTEXT_KEY);
        return value instanceof AgentToolEventListener listener
                ? listener
                : AgentToolEventListener.NO_OP;
    }
    /**
     * 原子占用一次工具调用预算；独立调用工具时没有计数器，因此不参与 Agent 预算。
     */
    private int reserveToolCall(ToolContext toolContext) {
        Object counterValue = toolContext.getContext().get(TOOL_CALL_COUNTER_CONTEXT_KEY);
        if (counterValue == null) {
            return 0;
        }
        if (!(counterValue instanceof AtomicInteger counter)) {
            throw invalid("AI_TOOL_CONTEXT_INVALID", "AI 工具调用计数器无效");
        }
        Object limitValue = toolContext.getContext().get(TOOL_CALL_LIMIT_CONTEXT_KEY);
        int limit = limitValue instanceof Number number ? number.intValue() : 6;
        int invocation = counter.incrementAndGet();
        if (limit <= 0 || invocation > limit) {
            throw new BusinessException(
                    "AI_TOOL_CALL_LIMIT_EXCEEDED",
                    "本轮 AI 工具调用次数已达上限",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        return invocation;
    }

    /**
     * 计算工具执行耗时并转换为毫秒。
     */
    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
    /**
     * 将工具参数转换为统一房源检索约束。
     */
    private IntentResult buildIntent(HouseSearchToolRequest request) {
        if (request == null) {
            throw invalid("AI_TOOL_ARGUMENT_INVALID", "房源搜索参数不能为空");
        }
        String normalizedQuery = normalizeRequiredText(request.query(), "query", MAX_QUERY_LENGTH);
        String normalizedRegion = normalizeOptionalText(request.region(), "region", MAX_REGION_LENGTH);
        validatePrice(request.minimumPrice(), "minimum_price");
        validatePrice(request.maximumPrice(), "maximum_price");
        validatePrice(request.targetPrice(), "target_price");
        if (request.minimumPrice() != null
                && request.maximumPrice() != null
                && request.minimumPrice().compareTo(request.maximumPrice()) > 0) {
            throw invalid("AI_TOOL_PRICE_RANGE_INVALID", "最低价格不能高于最高价格");
        }
        validateRoomCount(request.bedroomCount(), "bedroom_count");
        validateRoomCount(request.minimumBedroomCount(), "minimum_bedroom_count");
        validateRoomCount(request.minimumLivingRoomCount(), "minimum_living_room_count");
        validateRoomCount(request.minimumBathroomCount(), "minimum_bathroom_count");
        validateRoomCount(request.minimumKitchenCount(), "minimum_kitchen_count");
        if (request.bedroomCount() != null && request.minimumBedroomCount() != null) {
            throw invalid("AI_TOOL_BEDROOM_CONSTRAINT_CONFLICT", "不能同时指定精确卧室数量和最少卧室数量");
        }

        List<SearchConstraint> constraints = new ArrayList<>();
        addConstraint(constraints, PRICE, GTE, request.minimumPrice(), HARD);
        addConstraint(constraints, PRICE, LTE, request.maximumPrice(), HARD);
        addConstraint(constraints, PRICE, AROUND, request.targetPrice(), SOFT);
        addConstraint(constraints, BEDROOMS, EQ, request.bedroomCount(), HARD);
        addConstraint(constraints, BEDROOMS, GTE, request.minimumBedroomCount(), HARD);
        addConstraint(constraints, LIVING_ROOMS, GTE, request.minimumLivingRoomCount(), HARD);
        addConstraint(constraints, BATHROOMS, GTE, request.minimumBathroomCount(), HARD);
        addConstraint(constraints, KITCHENS, GTE, request.minimumKitchenCount(), HARD);
        addConstraint(constraints, REGION, CONTAINS, normalizedRegion, HARD);
        return new IntentResult(
                IntentResult.Intent.HOUSE_RECOMMEND,
                constraints,
                null,
                normalizedQuery,
                "");
    }

    /**
     * 仅在参数存在时增加结构化检索约束。
     */
    private void addConstraint(List<SearchConstraint> constraints,
                               SearchConstraint.Field field,
                               SearchConstraint.Operator operator,
                               Object value,
                               SearchConstraint.Strength strength) {
        if (value != null) {
            constraints.add(new SearchConstraint(field, operator, value, strength));
        }
    }

    /**
     * 将数据库房源详情转换为模型可见的安全字段。
     */
    private HouseToolDetail safeDetail(Long houseId, Long userId) {
        HouseDetailView detail = houseService.detail(houseId, userId);
        return new HouseToolDetail(
                detail.id(),
                detail.title(),
                truncate(detail.description(), MAX_DESCRIPTION_LENGTH),
                detail.price(),
                detail.area(),
                detail.rooms(),
                detail.bedroomCount(),
                detail.livingRoomCount(),
                detail.bathroomCount(),
                detail.kitchenCount(),
                detail.regionName(),
                detail.image());
    }

    /**
     * 将安全房源详情转换为确定性的比较字段。
     */
    private HouseComparisonItem comparisonItem(HouseToolDetail detail) {
        return new HouseComparisonItem(
                detail.id(),
                detail.title(),
                detail.price(),
                detail.area(),
                detail.rooms(),
                detail.regionName());
    }

    /**
     * 读取服务端注入的当前用户身份，禁止模型通过参数伪造用户编号。
     */
    private Long requireUserId(ToolContext toolContext) {
        if (toolContext == null) {
            throw new BusinessException(
                    "AI_TOOL_CONTEXT_INVALID",
                    "AI 工具缺少用户上下文",
                    HttpStatus.UNAUTHORIZED);
        }
        Object value = toolContext.getContext().get(USER_ID_CONTEXT_KEY);
        if (value instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        throw new BusinessException(
                "AI_TOOL_CONTEXT_INVALID",
                "AI 工具用户上下文无效",
                HttpStatus.UNAUTHORIZED);
    }

    /**
     * 校验房源编号。
     */
    private Long requireHouseId(Long houseId) {
        if (houseId == null || houseId <= 0) {
            throw invalid("AI_TOOL_HOUSE_ID_INVALID", "房源编号无效");
        }
        return houseId;
    }

    /**
     * 校验房源比较数量和重复编号。
     */
    private List<Long> normalizeComparisonIds(List<Long> houseIds) {
        if (houseIds == null || houseIds.size() < 2 || houseIds.size() > 5) {
            throw invalid("AI_TOOL_COMPARE_COUNT_INVALID", "房源比较数量必须在 2 到 5 之间");
        }
        List<Long> normalizedHouseIds = houseIds.stream()
                .map(this::requireHouseId)
                .toList();
        if (new LinkedHashSet<>(normalizedHouseIds).size() != normalizedHouseIds.size()) {
            throw invalid("AI_TOOL_COMPARE_DUPLICATE", "房源比较编号不能重复");
        }
        return normalizedHouseIds;
    }

    /**
     * 规范化工具返回数量。
     */
    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(MAX_SEARCH_LIMIT, Math.max(1, requestedLimit));
    }

    /**
     * 校验价格为正数。
     */
    private void validatePrice(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) {
            throw invalid("AI_TOOL_PRICE_INVALID", field + " 必须大于 0");
        }
    }

    /**
     * 校验户型数量范围。
     */
    private void validateRoomCount(Integer value, String field) {
        if (value != null && (value < 0 || value > MAX_ROOM_COUNT)) {
            throw invalid("AI_TOOL_ROOM_COUNT_INVALID", field + " 必须在 0 到 20 之间");
        }
    }

    /**
     * 规范化必填文本并限制长度。
     */
    private String normalizeRequiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid("AI_TOOL_TEXT_REQUIRED", field + " 不能为空");
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw invalid("AI_TOOL_TEXT_TOO_LONG", field + " 长度超过限制");
        }
        return normalizedValue;
    }

    /**
     * 规范化可选文本并限制长度。
     */
    private String normalizeOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw invalid("AI_TOOL_TEXT_TOO_LONG", field + " 长度超过限制");
        }
        return normalizedValue;
    }

    /**
     * 限制不可信房源描述进入模型上下文的长度。
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(value.length(), maxLength));
    }

    /**
     * 创建工具参数错误类型的业务异常。
     */
    private BusinessException invalid(String code, String detail) {
        return new BusinessException(code, detail, HttpStatus.BAD_REQUEST);
    }

    /**
     * Agent 房源检索工具结果。
     */
    public record HouseSearchToolResult(String status, List<AiHouseView> houses) {
        public HouseSearchToolResult {
            houses = List.copyOf(houses);
        }
    }

    /**
     * 可提供给模型的公开房源详情。
     */
    public record HouseToolDetail(
            Long id,
            String title,
            String description,
            BigDecimal price,
            Integer area,
            String rooms,
            Integer bedroomCount,
            Integer livingRoomCount,
            Integer bathroomCount,
            Integer kitchenCount,
            String regionName,
            String image) {
    }

    /**
     * 用于确定性比较的房源字段。
     */
    public record HouseComparisonItem(
            Long id,
            String title,
            BigDecimal price,
            Integer area,
            String rooms,
            String regionName) {
    }
}
