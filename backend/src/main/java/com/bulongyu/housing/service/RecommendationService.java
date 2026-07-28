package com.bulongyu.housing.service;

import com.bulongyu.housing.vo.HouseListView;


import com.bulongyu.housing.service.ai.SemanticRetriever;
import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.HouseQuery;
import com.bulongyu.housing.entity.HouseRow;
import com.bulongyu.housing.mapper.HouseMapper;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * 房源推荐业务服务
 */
@Service
public class RecommendationService {
    private static final HouseQuery ALL = new HouseQuery(null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);
    private final HouseMapper houses;
    private final UserMapper users;
    private final JdbcTemplate jdbc;
    private final SemanticRetriever vectors;

    /**
     * 初始化 {@code RecommendationService} 并注入所需依赖。
     *
     * @param houses 候选房源列表
     * @param users 用户数据访问组件
     * @param jdbc 数据库访问组件
     * @param vectors 向量相似度集合
     */
    public RecommendationService(HouseMapper houses, UserMapper users, JdbcTemplate jdbc,
                                 SemanticRetriever vectors) {
        this.houses = houses;
        this.users = users;
        this.jdbc = jdbc;
        this.vectors = vectors;
    }

    /**
     * 查询与指定房源相似的推荐房源。
     *
     * @param houseId 房源编号
     * @param userId 用户编号
     * @param requestedLimit 请求的返回数量上限
     */
    public List<HouseListView> similar(Long houseId, Long userId, int requestedLimit) {
        // 1. 目标房源必须是公开有效房源，避免利用推荐接口探测未审核或已下架数据。
        HouseRow target = houses.findById(houseId);
        if (target == null || !"approved".equals(target.status())
                || !Boolean.TRUE.equals(target.active())) {
            throw new BusinessException("HOUSE_NOT_FOUND", "房源不存在", HttpStatus.NOT_FOUND);
        }
        int limit = limit(requestedLimit);
        // 2. 向量召回提供语义相似度；最终候选仍来自 MySQL 的公开房源查询。
        SemanticRetriever.Retrieval semantic = vectors.retrieveHouseIds(
                target.title() + " " + target.description() + " " + target.rooms() + " " + target.fullRegionName(), 50);
        List<HouseRow> candidates = all().stream()
                .filter(house -> !house.id().equals(houseId))
                .toList();
        Map<Long, Integer> points = points();
        // 3. 结构化相似度、向量分数和推荐点只影响排序，不改变公开房源边界。
        return rank(candidates,
                house -> similarity(target, house)
                        + semantic.scores().getOrDefault(house.id(), 0.0) * 0.25
                        + pointBoost(points.getOrDefault(house.id(), 0)),
                limit);
    }

    /**
     * 根据用户行为与偏好生成个性化房源推荐。
     *
     * @param authUserId 当前登录用户编号
     * @param requestedLimit 请求的返回数量上限
     */
    public List<HouseListView> forUser(Long authUserId, int requestedLimit) {
        UserProfile profile = users.findProfileByUserId(authUserId);
        if (profile == null) {
            throw new BusinessException("PROFILE_NOT_FOUND", "用户资料不存在", HttpStatus.BAD_REQUEST);
        }
        int limit = limit(requestedLimit);
        // 1. 收藏和浏览历史共同构成显式偏好，已交互房源不会再次进入候选集。
        List<Long> preferredIds = jdbc.query("""
                SELECT house_id FROM favorite WHERE user_id=?
                UNION SELECT house_id FROM browse_history WHERE user_id=?
                """, (rs, n) -> rs.getLong(1), profile.id(), profile.id());
        List<HouseRow> candidates = all().stream().filter(h -> !preferredIds.contains(h.id())).toList();
        Map<Long, Integer> points = points();
        // 2. 冷启动时没有个性偏好，退化为推荐点与热度排序。
        if (preferredIds.isEmpty()) {
            return rank(candidates,
                    house -> pointBoost(points.getOrDefault(house.id(), 0))
                            + house.clickCount() / 10000.0,
                    limit);
        }
        List<HouseRow> preferred = preferredIds.stream()
                .map(houses::findById)
                .filter(Objects::nonNull)
                .toList();
        double avgPrice = preferred.stream().mapToDouble(h -> h.price().doubleValue()).average().orElse(0);
        double avgArea = preferred.stream().mapToInt(HouseRow::area).average().orElse(0);
        Map<Long, Long> regionFrequency = new HashMap<>();
        for (HouseRow house : preferred) {
            regionFrequency.merge(house.regionId(), 1L, Long::sum);
        }
        Set<Integer> preferredBedrooms = new HashSet<>();
        preferred.forEach(house -> preferredBedrooms.add(house.bedroomCount()));
        // 3. 历史均价、面积、地区频次和户型属于软偏好，仅用于候选排序。
        return rank(candidates,
                house -> preference(house, avgPrice, avgArea, regionFrequency, preferredBedrooms)
                        + pointBoost(points.getOrDefault(house.id(), 0)),
                limit);
    }

    /**
     * 计算两套房源之间的特征相似度。
     *
     * @param a 目标房源
     * @param b 对比房源
     */
    private double similarity(HouseRow a, HouseRow b) {
        double price = 1 - Math.min(1, Math.abs(a.price().doubleValue() - b.price().doubleValue()) / 19500.0);
        double area = 1 - Math.min(1, Math.abs(a.area() - b.area()) / 480.0);
        double rooms = Objects.equals(a.bedroomCount(), b.bedroomCount()) ? 1 : 0;
        double region = Objects.equals(a.regionId(), b.regionId()) ? 1 : 0;
        return price * .30 + area * .25 + rooms * .25 + region * .20;
    }

    /**
     * 计算房源与用户历史偏好的匹配度。
     *
     * @param h 候选房源
     * @param avgPrice avg租金
     * @param avgArea avg地区
     * @param regions 地区数据集合
     * @param bedrooms 卧室数量
     */
    private double preference(HouseRow h, double avgPrice, double avgArea,
                              Map<Long, Long> regions, Set<Integer> bedrooms) {
        double price = 1 - Math.min(1, Math.abs(h.price().doubleValue() - avgPrice) / 19500.0);
        double area = 1 - Math.min(1, Math.abs(h.area() - avgArea) / 480.0);
        double rooms = bedrooms.contains(h.bedroomCount()) ? 1 : 0;
        long max = regions.values().stream().mapToLong(Long::longValue).max().orElse(1);
        double region = regions.getOrDefault(h.regionId(), 0L) / (double) max;
        return price * .15 + area * .15 + rooms * .20 + region * .50;
    }

    /**
     * 计算候选房源的综合推荐排序。
     *
     * @param values 待处理的数据集合
     * @param scorer 排序函数
     * @param limit 返回数量上限
     */
    private List<HouseListView> rank(List<HouseRow> values, ToDoubleFunction<HouseRow> scorer, int limit) {
        return values.stream()
                .sorted(Comparator.comparingDouble(scorer)
                        .reversed()
                        .thenComparing(HouseRow::createTime, Comparator.reverseOrder()))
                .limit(limit)
                .map(HouseListView::from)
                .toList();
    }
    /**
     * 查询参与推荐计算的公开房源候选集。
     */
    private List<HouseRow> all() {
        return houses.findPublic(ALL, 0, 500);
    }
    /**
     * 查询房源当前获得的推荐点数。
     */
    private Map<Long, Integer> points() {
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT house_id,points FROM recommend_point")) {
            result.put(((Number) row.get("house_id")).longValue(),
                    ((Number) row.get("points")).intValue());
        }
        return result;
    }
    /**
     * 计算推荐点数对排序分数的提升值。
     *
     * @param points 积分
     */
    private double pointBoost(int points) {
        return Math.min(100, Math.max(0, points)) / 1000.0;
    }
    /**
     * 将每页数量限制在允许范围内。
     *
     * @param value 字段值
     */
    private int limit(int value) {
        return Math.min(20, Math.max(1, value));
    }
}
