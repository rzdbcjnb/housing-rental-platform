package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.entity.SearchConstraint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 大模型意图结果白名单解析器
 */
final class ModelIntentParser {
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 大模型意图结果白名单解析器
     *
     * @param response 响应
     * @param fallback 确定性规则识别结果
     */
    IntentResult merge(String response, IntentResult fallback) {
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            // 只解析完整 JSON 对象，模型夹带的说明文本不会直接进入约束编译。
            if (start < 0 || end <= start) {
                return fallback;
            }
            JsonNode root = json.readTree(response.substring(start, end + 1));
            IntentResult.Intent modelIntent = parseIntent(root.path("intent").asText());
            List<SearchConstraint> modelConstraints = parseConstraints(root.path("params").path("constraints"));
            Map<String, SearchConstraint> merged = new LinkedHashMap<>();
            fallback.constraints().forEach(c -> merged.put(key(c), c));
            modelConstraints.forEach(c -> merged.putIfAbsent(key(c), c));
            IntentResult.Intent intent = fallback.intent() == IntentResult.Intent.GENERAL_CHAT
                    ? modelIntent : fallback.intent();
            String searchQuery = clean(root.path("search_query").asText(), 500);
            String clarification = clean(root.path("clarification").asText(), 200);
            Long houseId = fallback.houseId();
            if (root.path("params").path("house_id").canConvertToLong()) {
                houseId = root.path("params").path("house_id").longValue();
            }
            return new IntentResult(intent, new ArrayList<>(merged.values()), houseId,
                    searchQuery.isBlank() ? fallback.searchQuery() : searchQuery, clarification);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    /**
     * 大模型意图结果白名单解析器
     *
     * @param node JSON 节点
     */
    private List<SearchConstraint> parseConstraints(JsonNode node) {
        if (!node.isArray() || node.size() > 12) {
            return List.of();
        }
        List<SearchConstraint> result = new ArrayList<>();
        for (JsonNode item : node) {
            try {
                SearchConstraint.Field field = SearchConstraint.Field.valueOf(
                        item.path("field").asText().toUpperCase(Locale.ROOT));
                SearchConstraint.Operator operator = SearchConstraint.Operator.valueOf(
                        item.path("operator").asText().toUpperCase(Locale.ROOT));
                SearchConstraint.Strength strength = SearchConstraint.Strength.valueOf(
                        item.path("strength").asText().toUpperCase(Locale.ROOT));
                Object value;
                if (field == SearchConstraint.Field.REGION) {
                    if (operator != SearchConstraint.Operator.CONTAINS
                            && operator != SearchConstraint.Operator.EQ) {
                        continue;
                    }
                    value = clean(item.path("value").asText(), 30);
                    if (((String) value).isBlank()) {
                        continue;
                    }
                } else {
                    if (!item.path("value").isNumber()
                            || operator == SearchConstraint.Operator.CONTAINS) {
                        continue;
                    }
                    BigDecimal number = item.path("value").decimalValue();
                    if (number.signum() < 0) {
                        continue;
                    }
                    value = field == SearchConstraint.Field.PRICE ? number : number.intValueExact();
                    if (operator == SearchConstraint.Operator.AROUND) {
                        strength = SearchConstraint.Strength.SOFT;
                    }
                }
                result.add(new SearchConstraint(field, operator, value, strength));
            } catch (RuntimeException ignored) {
                // 大模型生成的非法约束不会进入 SQL 编译流程。
            }
        }
        return result;
    }

    /**
     * 大模型意图结果白名单解析器
     *
     * @param value 字段值
     */
    private IntentResult.Intent parseIntent(String value) {
        return IntentResult.Intent.valueOf(value.toUpperCase(Locale.ROOT));
    }

    /**
     * 大模型意图结果白名单解析器
     *
     * @param constraint 搜索约束
     */
    private String key(SearchConstraint constraint) {
        return constraint.field() + ":" + constraint.operator();
    }

    /**
     * 大模型意图结果白名单解析器
     *
     * @param value 字段值
     * @param maxLength 最大允许长度
     */
    private String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String clean = value.strip();
        return clean.substring(0, Math.min(maxLength, clean.length()));
    }
}
