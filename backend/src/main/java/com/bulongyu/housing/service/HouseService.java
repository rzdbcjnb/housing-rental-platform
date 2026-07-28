package com.bulongyu.housing.service;

import com.bulongyu.housing.dto.HouseUpsertRequest;
import com.bulongyu.housing.vo.HouseDetailResponse;
import com.bulongyu.housing.vo.HouseDetailView;
import com.bulongyu.housing.vo.HouseListView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bulongyu.housing.common.PageResponse;
import com.bulongyu.housing.service.PublishingService;
import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.House;
import com.bulongyu.housing.entity.HouseQuery;
import com.bulongyu.housing.entity.HouseRow;
import com.bulongyu.housing.mapper.HouseMapper;
import com.bulongyu.housing.service.HouseNotificationService;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 房源业务服务
 */
@Service
public class HouseService {
    private static final Logger log = LoggerFactory.getLogger(HouseService.class);

    private static final Set<String> HOUSE_TYPES = Set.of("whole", "share");
    private final HouseMapper houseMapper;
    private final UserMapper userMapper;
    private final PublishingService publishingService;
    private final HouseNotificationService houseNotificationService;
    private final ImageStorageService imageStorageService;

    /**
     * 初始化 {@code HouseService} 并注入所需依赖。
     *
     * @param houseMapper 房源数据访问组件
     * @param userMapper 用户数据访问组件
     * @param publishingService 房源发布资格服务
     * @param houseNotificationService 房源通知Service
     * @param imageStorageService 图片存储服务
     */
    public HouseService(HouseMapper houseMapper, UserMapper userMapper,
                        PublishingService publishingService,
                        HouseNotificationService houseNotificationService,
                        ImageStorageService imageStorageService) {
        this.houseMapper = houseMapper;
        this.userMapper = userMapper;
        this.publishingService = publishingService;
        this.houseNotificationService = houseNotificationService;
        this.imageStorageService = imageStorageService;
    }

    /**
     * 根据筛选条件分页查询已发布房源。
     *
     * @param query 用户输入的问题
     * @param requestedPage 请求页码
     * @param requestedPageSize 请求的每页数量
     * @return 房源分页结果
     */
    public PageResponse<HouseListView> list(HouseQuery query, int requestedPage,
                                            int requestedPageSize) {
        int currentPage = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize);

        // totalCount 统计全部符合筛选条件的公开房源；offset 只控制当前页查询起点。
        long totalCount = houseMapper.countPublic(query);
        int offset = (currentPage - 1) * pageSize;
        List<HouseListView> results = houseMapper.findPublic(query, offset, pageSize)
                .stream()
                .map(HouseListView::from)
                .toList();

        return page(totalCount, currentPage, pageSize, "/api/houses/", results);
    }

    /**
     * 查询房源详情并补充房东与户型信息。
     *
     * @param id 编号
     * @param userId 用户编号
     * @return 房源详情
     */
    public HouseDetailView detail(Long id, Long userId) {
        HouseRow row = requireHouse(id);
        UserProfile profile = userId == null ? null : userMapper.findProfileByUserId(userId);
        boolean canManage = profile != null
                && ("admin".equals(profile.role()) || profile.id().equals(row.landlordId()));
        if (Boolean.TRUE.equals(row.active()) && "approved".equals(row.status())) {
            return HouseDetailView.from(row, canManage);
        }
        // 非公开房源仅对管理员和所属房东可见；其余访问统一返回 404，避免泄露房源存在性。

        if (!canManage) {
            throw new BusinessException("HOUSE_NOT_FOUND", "\u623f\u6e90\u4e0d\u5b58\u5728", HttpStatus.NOT_FOUND);
        }
        return HouseDetailView.from(row);
    }

    /**
     * 分页查询当前房东发布的房源。
     *
     * @param userId 用户编号
     * @param keyword 搜索关键字
     * @param requestedPage 请求页码
     * @param requestedPageSize 请求的每页数量
     * @return 当前房东的房源分页结果
     */
    public PageResponse<HouseListView> myHouses(Long userId, String keyword,
                                                int requestedPage, int requestedPageSize) {
        UserProfile profile = requirePublisher(userId);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        int currentPage = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize);

        // 统计与列表查询使用同一房东和关键字条件，保证分页总数与当前页结果一致。
        long totalCount = houseMapper.countByLandlord(profile.id(), normalizedKeyword);
        int offset = (currentPage - 1) * pageSize;
        List<HouseListView> results = houseMapper
                .findByLandlord(profile.id(), normalizedKeyword, offset, pageSize)
                .stream()
                .map(HouseListView::from)
                .toList();

        return page(totalCount, currentPage, pageSize, "/api/houses/my/", results);
    }

    /**
     * 校验发布资格，并在同一事务中写入房源与户型信息。
     *
     * @param userId 用户编号
     * @param request 请求参数
     * @return 新建房源的详情
     */
    @Transactional
    public HouseDetailView create(Long userId, HouseUpsertRequest request) {
        log.info("创建房源，参数：userId={}，title={}，price={}，regionId={}",
                userId, request.title(), request.price(), request.region());
        UserProfile profile = requirePublisher(userId);
        // 1. 校验房东身份并预占发布资格，后续任一步骤失败都会由事务统一回滚。
        Long paymentId = publishingService.reserveForCreate(profile.id());
        // 2. 合并并校验请求字段，随后写入待审核房源。
        House house = new House();
        merge(house, request, true);
        LocalDateTime now = LocalDateTime.now();
        house.setLandlordId(profile.id());
        house.setStatus("pending");
        house.setClickCount(0);
        house.setActive(true);
        house.setCreateTime(now);
        house.setUpdateTime(now);
        houseMapper.insert(house);
        // 3. 核销发布资格，并通知管理员审核新房源。
        publishingService.consume(paymentId, house.getId());
        houseNotificationService.newHouse(requireHouse(house.getId()));
        log.info("完成房源创建，参数：houseId={}，landlordProfileId={}，status=pending",
                house.getId(), profile.id());
        return HouseDetailView.from(requireHouse(house.getId()));
    }

    /**
     * 校验房源归属并更新房源与户型信息。
     *
     * @param userId 用户编号
     * @param id 编号
     * @param request 请求参数
     * @return 更新后的房源详情
     */
    @Transactional
    public HouseDetailView update(Long userId, Long id,
                                               HouseUpsertRequest request) {
        log.info("更新房源，参数：userId={}，houseId={}，title={}，price={}",
                userId, id, request.title(), request.price());
        // 先校验归属关系再复制持久化状态，避免用请求数据绕过房源权限边界。
        HouseRow existing = requireOwnedHouse(userId, id);
        UserProfile actor = requireProfile(userId);
        House house = copy(existing);
        merge(house, request, false);
        if (!"admin".equals(actor.role())) {
            house.setStatus("pending");
            house.setActive(true);
        }
        house.setUpdateTime(LocalDateTime.now());
        houseMapper.update(house);
        HouseRow updated = requireHouse(id);
        if (!"admin".equals(actor.role())) {
            houseNotificationService.newHouse(updated);
        }
        if (!Objects.equals(existing.image(), updated.image())) {
            imageStorageService.deleteManagedAfterCommit(existing.image());
        }
        return HouseDetailView.from(updated);
    }

    /**
     * 校验房源归属并删除房源。
     *
     * @param userId 用户编号
     * @param id 编号
     */
    @Transactional
    public void delete(Long userId, Long id) {
        HouseRow existing = requireOwnedHouse(userId, id);
        houseMapper.delete(id);
        imageStorageService.deleteManagedAfterCommit(existing.image());
        log.info("完成房源删除，参数：houseId={}，actorUserId={}", id, userId);
    }

    /**
     * 审核房源并同步更新发布状态。
     *
     * @param userId 用户编号
     * @param id 编号
     * @param action 操作类型
     */
    @Transactional
    public HouseDetailResponse audit(Long userId, Long id, String action) {
        UserProfile profile = requireProfile(userId);
        if (!"admin".equals(profile.role())) {
            throw new BusinessException("ADMIN_REQUIRED", "\u9700\u8981\u7ba1\u7406\u5458\u6743\u9650", HttpStatus.FORBIDDEN);
        }
        HouseRow auditedHouse = requireHouse(id);
        // 状态写入、退款和通知均处于同一事务；任一步失败时整体回滚，避免状态与资金记录不一致。
        return switch (action == null ? "" : action) {
            case "approve" -> {
                houseMapper.updateStatus(id, "approved", true, LocalDateTime.now());
                houseNotificationService.audit(auditedHouse, profile.id(), "approved");
                yield new HouseDetailResponse("\u5ba1\u6838\u901a\u8fc7");
            }
            case "reject" -> {
                houseMapper.updateStatus(id, "rejected", true, LocalDateTime.now());
                publishingService.refundForRejectedHouse(id);
                houseNotificationService.audit(auditedHouse, profile.id(), "rejected");
                yield new HouseDetailResponse("\u5ba1\u6838\u4e0d\u901a\u8fc7");
            }
            case "offline" -> {
                houseMapper.updateStatus(id, "offline", false, LocalDateTime.now());
                houseNotificationService.offline(auditedHouse, profile.id());
                yield new HouseDetailResponse("\u5df2\u4e0b\u67b6");
            }
            default -> throw new BusinessException("INVALID_AUDIT_ACTION", "\u65e0\u6548\u64cd\u4f5c", HttpStatus.BAD_REQUEST);
        };
    }

    /**
     * 将创建或更新请求合并到房源实体，并统一校验和生成户型描述。
     *
     * @param house 候选房源
     * @param request 请求参数
     * @param create 创建
     */
    private void merge(House house, HouseUpsertRequest request, boolean create) {
        house.setTitle(value(request.title(), house.getTitle(), create, "title").trim());
        house.setDescription(defaultText(request.description(), house.getDescription()));
        house.setPrice(value(request.price(), house.getPrice(), create, "price"));
        house.setArea(value(request.area(), house.getArea(), create, "area"));
        house.setBedroomCount(value(request.bedroomCount(), house.getBedroomCount(), create, "bedroom_count"));
        house.setLivingRoomCount(value(request.livingRoomCount(), house.getLivingRoomCount(), create, "living_room_count"));
        house.setBathroomCount(value(request.bathroomCount(), house.getBathroomCount(), create, "bathroom_count"));
        house.setKitchenCount(value(request.kitchenCount(), house.getKitchenCount(), create, "kitchen_count"));
        house.setHouseType(value(request.houseType(), house.getHouseType(), create, "house_type"));
        house.setRegionId(value(request.region(), house.getRegionId(), create, "region"));
        house.setAddressDetail(defaultText(request.addressDetail(), house.getAddressDetail()));
        house.setImage(defaultText(request.image(), house.getImage()));
        validate(house);
        house.setRooms("%d\u5ba4%d\u5385%d\u536b%d\u53a8".formatted(
                house.getBedroomCount(), house.getLivingRoomCount(),
                house.getBathroomCount(), house.getKitchenCount()));
    }

    /**
     * 校验房源请求中的价格、面积和户型数据。
     *
     * @param house 候选房源
     */
    private void validate(House house) {
        if (house.getTitle().length() < 2) {
            invalid("title", "\u6807\u9898\u81f3\u5c11 2 \u4e2a\u5b57\u7b26");
        }
        if (house.getPrice().signum() <= 0 || house.getArea() < 1) {
            invalid("house", "\u4ef7\u683c\u548c\u9762\u79ef\u5fc5\u987b\u5927\u4e8e 0");
        }
        if (house.getBedroomCount() < 0 || house.getLivingRoomCount() < 0
                || house.getBathroomCount() < 1 || house.getKitchenCount() < 0) {
            invalid("rooms", "\u6237\u578b\u8ba1\u6570\u4e0d\u5408\u6cd5");
        }
        if (!HOUSE_TYPES.contains(house.getHouseType())) {
            invalid("house_type", "\u623f\u5c4b\u7c7b\u578b\u5fc5\u987b\u4e3a whole \u6216 share");
        }
    }

    /**
     * 查询房源并校验当前用户是否为房东。
     *
     * @param userId 用户编号
     * @param id 编号
     */
    private HouseRow requireOwnedHouse(Long userId, Long id) {
        HouseRow row = requireHouse(id);
        UserProfile profile = requireProfile(userId);
        if (!"admin".equals(profile.role()) && !profile.id().equals(row.landlordId())) {
            throw new BusinessException("HOUSE_FORBIDDEN", "\u53ea\u80fd\u7ba1\u7406\u81ea\u5df1\u7684\u623f\u6e90", HttpStatus.FORBIDDEN);
        }
        return row;
    }

    /**
     * 校验当前用户是否有权管理指定房源。
     *
     * @param userId 用户编号
     */
    private UserProfile requirePublisher(Long userId) {
        UserProfile profile = requireProfile(userId);
        if (!Set.of("landlord", "admin").contains(profile.role())) {
            throw new BusinessException("PUBLISHER_REQUIRED", "\u53ea\u6709\u623f\u4e1c\u53ef\u4ee5\u53d1\u5e03\u623f\u6e90", HttpStatus.FORBIDDEN);
        }
        return profile;
    }

    /**
     * 查询并校验用户资料。
     *
     * @param userId 用户编号
     */
    private UserProfile requireProfile(Long userId) {
        if (userId == null) {
            throw new BusinessException("AUTH_REQUIRED", "\u8bf7\u5148\u767b\u5f55", HttpStatus.UNAUTHORIZED);
        }
        UserProfile profile = userMapper.findProfileByUserId(userId);
        if (profile == null) {
            throw new BusinessException("PROFILE_NOT_FOUND", "\u7528\u6237\u8d44\u6599\u4e0d\u5b58\u5728", HttpStatus.FORBIDDEN);
        }
        return profile;
    }

    /**
     * 查询并校验房源存在。
     *
     * @param id 编号
     */
    private HouseRow requireHouse(Long id) {
        HouseRow row = houseMapper.findById(id);
        if (row == null) {
            throw new BusinessException("HOUSE_NOT_FOUND", "\u623f\u6e90\u4e0d\u5b58\u5728", HttpStatus.NOT_FOUND);
        }
        return row;
    }

    /**
     * 复制对象并应用请求中的更新字段。
     *
     * @param row 数据库查询结果
     */
    private House copy(HouseRow row) {
        // 完整复制持久化状态后再覆盖请求字段，确保 PATCH 未提供的字段保持原值。
        House house = new House();
        house.setId(row.id());
        house.setTitle(row.title());
        house.setDescription(row.description());
        house.setPrice(row.price());
        house.setArea(row.area());
        house.setRooms(row.rooms());
        house.setBedroomCount(row.bedroomCount());
        house.setLivingRoomCount(row.livingRoomCount());
        house.setBathroomCount(row.bathroomCount());
        house.setKitchenCount(row.kitchenCount());
        house.setHouseType(row.houseType());
        house.setRegionId(row.regionId());
        house.setAddressDetail(row.addressDetail());
        house.setImage(row.image());
        house.setLandlordId(row.landlordId());
        house.setStatus(row.status());
        house.setClickCount(row.clickCount());
        house.setActive(row.active());
        house.setCreateTime(row.createTime());
        house.setUpdateTime(row.updateTime());
        return house;
    }

    /**
     * 选择请求值或原值，用于房源局部更新。
     *
     * @param requestValue 请求中的新值
     * @param currentValue 当前值
     * @param required 是否要求该字段必须有值
     * @param field 约束字段
     */
    private <T> T value(T requestValue, T currentValue, boolean required, String field) {
        T result = requestValue == null ? currentValue : requestValue;
        if (required && result == null) {
            invalid(field, field + " is required");
        }
        return result;
    }

    /**
     * 为空文本提供默认值。
     *
     * @param requestValue 请求中的新值
     * @param currentValue 当前值
     */
    private String defaultText(String requestValue, String currentValue) {
        if (requestValue != null) {
            return requestValue.trim();
        }
        return currentValue == null ? "" : currentValue;
    }

    /**
     * 创建参数校验失败类型的业务异常。
     *
     * @param field 约束字段
     * @param message 消息
     */
    private void invalid(String field, String message) {
        throw new BusinessException("INVALID_" + field.toUpperCase(), message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 将页码限制在有效范围内。
     *
     * @param page 页码
     */
    private int normalizePage(int page) {
        return Math.max(1, page);
    }

    /**
     * 将每页数量限制在允许范围内。
     *
     * @param pageSize 页码每页数量
     */
    private int normalizePageSize(int pageSize) {
        return Math.min(100, Math.max(1, pageSize));
    }

    /**
     * 将页码限制在有效范围内。
     *
     * @param count 数量
     * @param current 当前页码
     * @param size 每页数量
     * @param path 资源路径
     * @param results 处理结果集合
     */
    private <T> PageResponse<T> page(long totalCount, int currentPage, int pageSize,
                                     String path, List<T> results) {
        String next = (long) currentPage * pageSize < totalCount
                ? path + "?page=" + (currentPage + 1) + "&page_size=" + pageSize
                : null;
        String previous = currentPage > 1
                ? path + "?page=" + (currentPage - 1) + "&page_size=" + pageSize
                : null;
        return new PageResponse<>(totalCount, next, previous, results);
    }
}
