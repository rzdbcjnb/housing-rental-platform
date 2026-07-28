package com.bulongyu.housing.service;

import com.bulongyu.housing.vo.FavoriteStatus;
import com.bulongyu.housing.vo.HouseSummary;
import com.bulongyu.housing.vo.InteractionCreatedView;
import com.bulongyu.housing.vo.InteractionDetailView;
import com.bulongyu.housing.vo.InteractionItemView;


import com.bulongyu.housing.common.PageResponse;
import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.HouseRow;
import com.bulongyu.housing.mapper.HouseMapper;
import com.bulongyu.housing.entity.InteractionEntity;
import com.bulongyu.housing.entity.InteractionRow;
import com.bulongyu.housing.mapper.InteractionMapper;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收藏与浏览历史业务服务
 */
@Service
public class InteractionService {
    private final InteractionMapper interactionMapper;
    private final HouseMapper houseMapper;
    private final UserMapper userMapper;

    /**
     * 初始化 {@code InteractionService} 并注入所需依赖。
     *
     * @param interactionMapper 收藏与浏览历史数据访问组件
     * @param houseMapper 房源数据访问组件
     * @param userMapper 用户数据访问组件
     */
    public InteractionService(InteractionMapper interactionMapper, HouseMapper houseMapper,
                              UserMapper userMapper) {
        this.interactionMapper = interactionMapper;
        this.houseMapper = houseMapper;
        this.userMapper = userMapper;
    }

    /**
     * 收藏指定房源并避免重复记录。
     *
     * @param userId 用户编号
     * @param houseId 房源编号
     */
    @Transactional
    public InteractionCreatedView addFavorite(Long userId, Long houseId) {
        UserProfile profile = requireProfile(userId);
        requirePublicHouse(houseId);
        // 应用层预检提供明确的业务提示，数据库唯一约束负责拦截并发重复收藏。
        if (interactionMapper.findFavoriteId(profile.id(), houseId) != null) {
            throw bad("FAVORITE_EXISTS", "\u5df2\u7ecf\u6536\u85cf\u8fc7\u8be5\u623f\u6e90");
        }
        InteractionEntity entity = entity(profile.id(), houseId);
        try {
            interactionMapper.insertFavorite(entity);
        } catch (DuplicateKeyException exception) {
            throw bad("FAVORITE_EXISTS", "\u5df2\u7ecf\u6536\u85cf\u8fc7\u8be5\u623f\u6e90");
        }
        return new InteractionCreatedView(entity.getId(), houseId, entity.getCreateTime());
    }

    /**
     * 取消收藏指定房源。
     *
     * @param userId 用户编号
     * @param favoriteId 收藏编号
     */
    @Transactional
    public InteractionDetailView removeFavorite(Long userId, Long favoriteId) {
        UserProfile profile = requireProfile(userId);
        if (interactionMapper.deleteFavorite(favoriteId, profile.id()) == 0) {
            throw notFound("FAVORITE_NOT_FOUND", "\u6536\u85cf\u4e0d\u5b58\u5728");
        }
        return new InteractionDetailView("\u53d6\u6d88\u6536\u85cf\u6210\u529f");
    }

    /**
     * 分页查询当前用户收藏的房源。
     *
     * @param userId 用户编号
     * @param page 页码
     * @param pageSize 页码每页数量
     */
    public PageResponse<InteractionItemView> favorites(Long userId, int page, int pageSize) {
        UserProfile profile = requireProfile(userId);
        int currentPage = Math.max(1, page);
        int normalizedPageSize = size(pageSize);

        // totalCount 是全部收藏记录数；offset 和 pageSize 只限定当前页数据库查询范围。
        long totalCount = interactionMapper.countFavorites(profile.id());
        int offset = (currentPage - 1) * normalizedPageSize;
        List<InteractionRow> currentPageRows = interactionMapper
                .findFavorites(profile.id(), offset, normalizedPageSize);
        return page(totalCount, currentPage, normalizedPageSize,
                "/api/houses/favorites/", currentPageRows);
    }

    /**
     * 查询当前用户是否已收藏指定房源。
     *
     * @param userId 用户编号
     * @param houseId 房源编号
     */
    public FavoriteStatus favoriteStatus(Long userId, Long houseId) {
        Long id = interactionMapper.findFavoriteId(requireProfile(userId).id(), houseId);
        return new FavoriteStatus(id != null, id);
    }

    /**
     * 新增或刷新房源浏览历史。
     *
     * @param userId 用户编号
     * @param houseId 房源编号
     */
    @Transactional
    public InteractionCreatedView addHistory(Long userId, Long houseId) {
        UserProfile profile = requireProfile(userId);
        requirePublicHouse(houseId);
        LocalDateTime now = LocalDateTime.now();
        Long existingId = interactionMapper.findHistoryId(profile.id(), houseId);
        // 浏览历史按“用户 + 房源”复用记录；重复浏览只刷新时间，不新增重复行。
        if (existingId != null) {
            interactionMapper.touchHistory(existingId, now);
            return new InteractionCreatedView(existingId, houseId, now);
        }
        InteractionEntity entity = entity(profile.id(), houseId);
        interactionMapper.insertHistory(entity);
        return new InteractionCreatedView(entity.getId(), houseId, entity.getCreateTime());
    }

    /**
     * 分页查询当前用户的房源浏览历史。
     *
     * @param userId 用户编号
     * @param page 页码
     * @param pageSize 页码每页数量
     */
    public PageResponse<InteractionItemView> history(Long userId, int page, int pageSize) {
        UserProfile profile = requireProfile(userId);
        int currentPage = Math.max(1, page);
        int normalizedPageSize = size(pageSize);

        long totalCount = interactionMapper.countHistory(profile.id());
        int offset = (currentPage - 1) * normalizedPageSize;
        List<InteractionRow> currentPageRows = interactionMapper
                .findHistory(profile.id(), offset, normalizedPageSize);
        return page(totalCount, currentPage, normalizedPageSize,
                "/api/houses/browse-history/", currentPageRows);
    }

    /**
     * 删除指定房源的浏览历史。
     *
     * @param userId 用户编号
     * @param historyId 历史消息编号
     */
    @Transactional
    public InteractionDetailView removeHistory(Long userId, Long historyId) {
        UserProfile profile = requireProfile(userId);
        if (interactionMapper.deleteHistory(historyId, profile.id()) == 0) {
            throw notFound("HISTORY_NOT_FOUND", "\u6d4f\u89c8\u5386\u53f2\u4e0d\u5b58\u5728");
        }
        return new InteractionDetailView("\u5220\u9664\u6210\u529f");
    }

    /**
     * 清空当前用户的全部浏览历史。
     *
     * @param userId 用户编号
     */
    @Transactional
    public InteractionDetailView clearHistory(Long userId) {
        int count = interactionMapper.clearHistory(requireProfile(userId).id());
        return new InteractionDetailView("\u5df2\u6e05\u7a7a" + count + "\u6761\u6d4f\u89c8\u5386\u53f2");
    }

    /**
     * 查询并校验用户资料。
     *
     * @param userId 用户编号
     */
    private UserProfile requireProfile(Long userId) {
        UserProfile profile = userMapper.findProfileByUserId(userId);
        if (profile == null) {
            throw bad("PROFILE_NOT_FOUND", "\u7528\u6237\u8d44\u6599\u4e0d\u5b58\u5728");
        }
        return profile;
    }

    /**
     * 查询房源并校验其是否处于公开发布状态。
     *
     * @param houseId 房源编号
     */
    private HouseRow requirePublicHouse(Long houseId) {
        HouseRow house = houseId == null ? null : houseMapper.findById(houseId);
        if (house == null || !"approved".equals(house.status()) || !Boolean.TRUE.equals(house.active())) {
            throw notFound("HOUSE_NOT_FOUND", "\u623f\u6e90\u4e0d\u5b58\u5728");
        }
        return house;
    }

    /**
     * 查询收藏或浏览记录对应的房源实体。
     *
     * @param profileId 用户资料编号
     * @param houseId 房源编号
     */
    private InteractionEntity entity(Long profileId, Long houseId) {
        InteractionEntity entity = new InteractionEntity();
        entity.setUserId(profileId);
        entity.setHouseId(houseId);
        entity.setCreateTime(LocalDateTime.now());
        return entity;
    }

    /**
     * 将页码限制在有效范围内。
     *
     * @param count 数量
     * @param requestedPage 请求页码
     * @param requestedSize 请求的每页数量
     * @param path 资源路径
     * @param rows 数据库查询结果集合
     */
    private PageResponse<InteractionItemView> page(long totalCount, int currentPage,
                                                    int pageSize, String path,
                                                    List<InteractionRow> currentPageRows) {
        List<InteractionItemView> results = currentPageRows.stream()
                .map(this::view)
                .toList();
        String next = (long) currentPage * pageSize < totalCount
                ? path + "?page=" + (currentPage + 1) + "&page_size=" + pageSize
                : null;
        String previous = currentPage > 1
                ? path + "?page=" + (currentPage - 1) + "&page_size=" + pageSize
                : null;
        return new PageResponse<>(totalCount, next, previous, results);
    }

    /**
     * 将房源实体转换为收藏或浏览历史视图。
     *
     * @param row 数据库查询结果
     */
    private InteractionItemView view(InteractionRow row) {
        return new InteractionItemView(row.id(),
                new HouseSummary(row.houseId(), row.title(), row.price(), row.area(),
                        row.rooms(), row.image(), row.fullRegionName()), row.createTime());
    }


    /**
     * 返回集合或分页结果包含的数据数量。
     *
     * @param value 字段值
     */
    private int size(int value) {
        return Math.min(100, Math.max(1, value));
    }

    /**
     * 创建参数错误类型的业务异常。
     *
     * @param code 业务错误码
     * @param detail 详情
     */
    private BusinessException bad(String code, String detail) {
        return new BusinessException(code, detail, HttpStatus.BAD_REQUEST);
    }

    /**
     * 创建资源不存在类型的业务异常。
     *
     * @param code 业务错误码
     * @param detail 详情
     */
    private BusinessException notFound(String code, String detail) {
        return new BusinessException(code, detail, HttpStatus.NOT_FOUND);
    }
}
