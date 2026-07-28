package com.bulongyu.housing.service.ai;

import com.bulongyu.housing.entity.HouseCandidate;
import com.bulongyu.housing.entity.IntentResult;
import com.bulongyu.housing.entity.SearchConstraint;
import com.bulongyu.housing.mapper.AiMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.bulongyu.housing.entity.SearchConstraint.Field.BATHROOMS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.BEDROOMS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.KITCHENS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.LIVING_ROOMS;
import static com.bulongyu.housing.entity.SearchConstraint.Field.PRICE;
import static com.bulongyu.housing.entity.SearchConstraint.Field.REGION;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.EQ;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.GTE;
import static com.bulongyu.housing.entity.SearchConstraint.Operator.LTE;
import static com.bulongyu.housing.entity.SearchConstraint.Strength.HARD;

/**
 * AI 房源统一检索服务。
 */
@Service
public class AiHouseSearchService {
    private static final Logger log = LoggerFactory.getLogger(AiHouseSearchService.class);
    private static final int MAX_RESULTS = 50;
    private static final int VECTOR_CANDIDATE_LIMIT = 50;

    private final SemanticRetriever semanticRetriever;
    private final AiMapper aiMapper;

    /**
     * 初始化 AI 房源统一检索服务。
     *
     * @param semanticRetriever 房源语义召回服务
     * @param aiMapper AI 数据访问组件
     */
    public AiHouseSearchService(SemanticRetriever semanticRetriever, AiMapper aiMapper) {
        this.semanticRetriever = semanticRetriever;
        this.aiMapper = aiMapper;
    }

    /**
     * 使用向量召回、MySQL 硬过滤和软偏好排序查询房源。
     *
     * @param intentResult 已通过白名单校验的意图识别结果
     * @param requestedLimit 请求返回的房源数量
     * @return 排序后的房源候选及向量库状态
     */
    public SearchResult search(IntentResult intentResult, int requestedLimit) {
        int resultLimit = Math.min(MAX_RESULTS, Math.max(1, requestedLimit));
        SemanticRetriever.Retrieval retrieval = semanticRetriever.retrieveHouseIds(
                intentResult.searchQuery(),
                VECTOR_CANDIDATE_LIMIT);
        boolean hasHardConstraints = intentResult.constraints().stream()
                .anyMatch(constraint -> constraint.strength() == HARD);

        if (retrieval.status() == SemanticRetriever.RetrievalStatus.SUCCESS_EMPTY) {
            return finish(intentResult, SearchStatus.NO_MATCH, List.of(), 0, false);
        }
        if (retrieval.status() == SemanticRetriever.RetrievalStatus.UNAVAILABLE
                && !hasHardConstraints) {
            return finish(intentResult, SearchStatus.RETRIEVAL_UNAVAILABLE, List.of(), 0, false);
        }

        CompiledFilters filters = compile(intentResult.constraints());
        // 硬约束必须由 MySQL 在完整公开房源集合上执行；没有硬约束时使用向量 ID 限定候选。
        List<Long> candidateIds = hasHardConstraints ? List.of() : retrieval.ids();
        List<HouseCandidate> candidates = aiMapper.searchHouses(
                candidateIds,
                filters.priceMin,
                filters.priceMax,
                filters.bedroomMin,
                filters.bedroomMax,
                filters.livingRoomMin,
                filters.bathroomMin,
                filters.kitchenMin,
                filters.region);

        List<HouseCandidate> sortedCandidates = new ArrayList<>(candidates);
        sortedCandidates.sort(Comparator
                .comparingDouble((HouseCandidate house) -> score(
                        house,
                        intentResult.constraints(),
                        retrieval.scores()))
                .reversed()
                .thenComparing(HouseCandidate::id));
        int totalCount = sortedCandidates.size();
        if (totalCount > resultLimit) {
            sortedCandidates = new ArrayList<>(sortedCandidates.subList(0, resultLimit));
        }
        SearchStatus status = retrieval.status() == SemanticRetriever.RetrievalStatus.UNAVAILABLE
                ? SearchStatus.DEGRADED_STRUCTURED
                : sortedCandidates.isEmpty() ? SearchStatus.NO_MATCH : SearchStatus.MATCHED;
        return finish(
                intentResult,
                status,
                List.copyOf(sortedCandidates),
                totalCount,
                !candidateIds.isEmpty());
    }

    /**
     * 记录统一检索状态并构造不可变结果。
     */
    private SearchResult finish(IntentResult intentResult,
                                SearchStatus status,
                                List<HouseCandidate> houses,
                                int candidateCount,
                                boolean vectorRestricted) {
        log.info("完成AI房源检索，参数：intent={}，status={}，vectorRestricted={}，candidateCount={}，resultCount={}",
                intentResult.intent(), status, vectorRestricted, candidateCount, houses.size());
        return new SearchResult(houses, status);
    }
    /**
     * 将硬约束编译为参数化 MyBatis 查询参数。
     *
     * @param constraints 经过白名单校验的检索约束
     * @return MySQL 硬过滤参数
     */
    private CompiledFilters compile(List<SearchConstraint> constraints) {
        CompiledFilters filters = new CompiledFilters();
        for (SearchConstraint constraint : constraints) {
            if (constraint.strength() != HARD) {
                continue;
            }
            if (constraint.field() == REGION) {
                filters.region = String.valueOf(constraint.value());
                continue;
            }
            if (!(constraint.value() instanceof Number number)) {
                continue;
            }
            int integerValue = number.intValue();
            BigDecimal decimalValue = new BigDecimal(number.toString());
            if (constraint.field() == PRICE) {
                applyPriceFilter(filters, constraint.operator(), decimalValue);
            } else if (constraint.field() == BEDROOMS) {
                applyBedroomFilter(filters, constraint.operator(), integerValue);
            } else if (constraint.field() == LIVING_ROOMS
                    && (constraint.operator() == GTE || constraint.operator() == EQ)) {
                filters.livingRoomMin = integerValue;
            } else if (constraint.field() == BATHROOMS
                    && (constraint.operator() == GTE || constraint.operator() == EQ)) {
                filters.bathroomMin = integerValue;
            } else if (constraint.field() == KITCHENS
                    && (constraint.operator() == GTE || constraint.operator() == EQ)) {
                filters.kitchenMin = integerValue;
            }
        }
        return filters;
    }

    /**
     * 编译价格上下界。
     */
    private void applyPriceFilter(CompiledFilters filters,
                                  SearchConstraint.Operator operator,
                                  BigDecimal value) {
        if (operator == GTE || operator == EQ) {
            filters.priceMin = value;
        }
        if (operator == LTE || operator == EQ) {
            filters.priceMax = value;
        }
    }

    /**
     * 编译卧室数量上下界。
     */
    private void applyBedroomFilter(CompiledFilters filters,
                                    SearchConstraint.Operator operator,
                                    int value) {
        if (operator == GTE || operator == EQ) {
            filters.bedroomMin = value;
        }
        if (operator == LTE || operator == EQ) {
            filters.bedroomMax = value;
        }
    }

    /**
     * 融合向量分数和软偏好匹配度计算排序分数。
     */
    private double score(HouseCandidate house,
                         List<SearchConstraint> constraints,
                         Map<Long, Double> vectorScores) {
        double totalScore = vectorScores.getOrDefault(house.id(), 0.0) * 0.35;
        for (SearchConstraint constraint : constraints) {
            if (constraint.strength() == HARD
                    || !(constraint.value() instanceof Number number)) {
                continue;
            }
            double target = number.doubleValue();
            if (constraint.field() == PRICE) {
                double difference = Math.abs(house.price().doubleValue() - target);
                totalScore += Math.max(0, 1 - difference / Math.max(target, 1)) * 0.35;
                continue;
            }
            Integer actual = roomCount(house, constraint.field());
            if (actual != null && matches(actual, number.intValue(), constraint.operator())) {
                totalScore += 0.15;
            }
        }
        return totalScore;
    }

    /**
     * 读取参与软偏好排序的户型数量。
     */
    private Integer roomCount(HouseCandidate house, SearchConstraint.Field field) {
        return switch (field) {
            case BEDROOMS -> house.bedroomCount();
            case LIVING_ROOMS -> house.livingRoomCount();
            case BATHROOMS -> house.bathroomCount();
            case KITCHENS -> house.kitchenCount();
            default -> null;
        };
    }

    /**
     * 判断实际户型数量是否匹配软偏好。
     */
    private boolean matches(int actual, int expected, SearchConstraint.Operator operator) {
        if (operator == EQ) {
            return actual == expected;
        }
        return actual >= expected;
    }

    /**
     * 统一检索对业务调用方暴露的状态。
     */
    public enum SearchStatus {
        MATCHED,
        NO_MATCH,
        DEGRADED_STRUCTURED,
        RETRIEVAL_UNAVAILABLE
    }

    /**
     * 房源检索结果。
     */
    public record SearchResult(List<HouseCandidate> houses, SearchStatus status) {
        public SearchResult {
            houses = List.copyOf(houses);
        }

        public boolean vectorActive() {
            return status == SearchStatus.MATCHED || status == SearchStatus.NO_MATCH;
        }
    }

    /**
     * 参数化 MyBatis 硬过滤条件。
     */
    private static final class CompiledFilters {
        private BigDecimal priceMin;
        private BigDecimal priceMax;
        private Integer bedroomMin;
        private Integer bedroomMax;
        private Integer livingRoomMin;
        private Integer bathroomMin;
        private Integer kitchenMin;
        private String region;
    }
}
