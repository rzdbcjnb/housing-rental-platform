package com.bulongyu.housing.service;

import com.bulongyu.housing.dto.AdminUserRequest;
import com.bulongyu.housing.vo.AdminDetailView;
import com.bulongyu.housing.vo.AdminHouseView;
import com.bulongyu.housing.vo.AdminStatusView;
import com.bulongyu.housing.vo.AdminUserView;


import com.bulongyu.housing.entity.AdminUserRow;
import com.bulongyu.housing.mapper.AdminMapper;
import com.bulongyu.housing.common.PageResponse;
import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.AuthUser;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import com.bulongyu.housing.security.DjangoPbkdf2PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 后台管理业务服务
 */
@Service
public class AdminService {
    private static final Set<String> ROLES = Set.of("tenant", "landlord", "admin");
    private final AdminMapper mapper;
    private final UserMapper users;
    private final DjangoPbkdf2PasswordEncoder passwords;
    private final JdbcTemplate jdbc;

    /**
     * 初始化 {@code AdminService} 并注入所需依赖。
     *
     * @param mapper 数据访问组件
     * @param users 用户数据访问组件
     * @param passwords 密码编码器
     * @param jdbc 数据库访问组件
     */
    public AdminService(AdminMapper mapper, UserMapper users, DjangoPbkdf2PasswordEncoder passwords,
                        JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.users = users;
        this.passwords = passwords;
        this.jdbc = jdbc;
    }

    /**
     * 按角色、状态和关键字分页查询用户。
     *
     * @param actorId 操作人编号
     * @param role 角色
     * @param active 是否启用
     * @param keyword 搜索关键字
     * @param page 页码
     * @param size 每页数量
     */
    public PageResponse<AdminUserView> users(Long actorId, String role, Boolean active,
                                             String keyword, int page, int size) {
        // 1. 权限校验必须先于统计和列表查询，避免向非管理员泄露后台数据规模。
        requireAdmin(actorId);

        // 2. 统计与列表查询复用同一组规范化条件，防止总数与当前页数据口径不一致。
        String normalizedRole = normalize(role);
        String normalizedKeyword = normalize(keyword);
        int currentPage = page(page);
        int pageSize = size(size);

        // 3. totalCount 是全部符合筛选条件的记录总数，不是当前页返回的数据量。
        long totalCount = mapper.countUsers(normalizedRole, active, normalizedKeyword);
        int offset = (currentPage - 1) * pageSize;
        List<AdminUserView> results = mapper
                .findUsers(normalizedRole, active, normalizedKeyword, offset, pageSize)
                .stream()
                .map(AdminUserView::from)
                .toList();

        // 4. 分页链接由总记录数、当前页和每页数量共同决定。
        return page(totalCount, currentPage, pageSize, "/api/admin/users/", results);
    }

    /**
     * 由管理员创建平台用户。
     *
     * @param actorId 操作人编号
     * @param request 请求参数
     * @return 创建后的用户信息
     */
    @Transactional
    public AdminUserView createUser(Long actorId, AdminUserRequest request) {
        // 1. 管理员权限和唯一性校验先于写入，数据库唯一约束继续兜住并发创建。
        requireAdmin(actorId);
        String username = required(request.username(), "用户名不能为空");
        if (users.countByUsername(username) > 0) {
            invalid("用户名已存在");
        }
        String role = role(request.role() == null ? "tenant" : request.role());
        String phone = request.phone() == null ? "" : request.phone().trim();
        if (!phone.isEmpty() && users.countByPhone(phone) > 0) {
            invalid("手机号已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        // 2. 先创建认证账号，再创建关联资料；两次写入共享事务，任一步失败都会回滚。
        String rawPassword = request.password() == null ? "test123456" : request.password();
        users.insertUser(username, passwords.encode(rawPassword), now);
        AuthUser user = users.findByUsername(username);
        users.insertProfile(user.id(), phone, role, now);
        return AdminUserView.from(mapper.findUser(user.id()));
    }

    /**
     * 由管理员更新用户资料与角色。
     *
     * @param actorId 操作人编号
     * @param id 编号
     * @param request 请求参数
     */
    @Transactional
    public AdminUserView updateUser(Long actorId, Long id, AdminUserRequest request) {
        requireAdmin(actorId);
        AuthUser current = users.findById(id);
        AdminUserRow row = mapper.findUser(id);
        if (current == null || row == null) {
            notFound("用户不存在");
        }
        String username = request.username() == null || request.username().isBlank() ? current.username() : request.username().trim();
        AuthUser sameName = users.findByUsername(username);
        if (sameName != null && !sameName.id().equals(id)) {
            invalid("用户名已存在");
        }
        String phone = request.phone() == null ? row.phone() : request.phone().trim();
        String role = request.role() == null ? row.role() : role(request.role());
        String password = request.password() == null || request.password().isBlank()
                ? current.password() : passwords.encode(request.password());
        boolean active = request.isActive() == null ? Boolean.TRUE.equals(current.active()) : request.isActive();
        // 认证账号与用户资料在同一事务中更新，避免角色、手机号与登录状态出现部分成功。
        mapper.updateUser(id, username, password, active);
        mapper.updateProfile(id, phone, role, LocalDateTime.now());
        return AdminUserView.from(mapper.findUser(id));
    }

    /**
     * 停用指定用户并使其无法继续登录。
     *
     * @param actorId 操作人编号
     * @param id 编号
     */
    @Transactional
    public AdminDetailView disableUser(Long actorId, Long id) {
        requireAdmin(actorId);
        AuthUser current = users.findById(id);
        AdminUserRow row = mapper.findUser(id);
        if (current == null || row == null) {
            notFound("用户不存在");
        }
        mapper.updateUser(id, current.username(), current.password(), false);
        return new AdminDetailView("用户已禁用");
    }

    /**
     * 更新用户或房源的业务状态。
     *
     * @param actorId 操作人编号
     * @param id 编号
     * @param active 是否启用
     */
    @Transactional
    public AdminStatusView status(Long actorId, Long id, Boolean active) {
        requireAdmin(actorId);
        AuthUser current = users.findById(id);
        if (current == null) {
            notFound("用户不存在");
        }
        boolean value = active == null ? Boolean.TRUE.equals(current.active()) : active;
        mapper.updateUser(id, current.username(), current.password(), value);
        return new AdminStatusView("状态更新成功", value);
    }

    /**
     * 按状态和关键字分页查询待管理房源。
     *
     * @param actorId 操作人编号
     * @param status 状态
     * @param keyword 搜索关键字
     * @param page 页码
     * @param size 每页数量
     */
    public PageResponse<AdminHouseView> houses(Long actorId, String status, String keyword,
                                               int page, int size) {
        // 1. 后台房源数量和列表都属于受保护信息，先完成管理员权限校验。
        requireAdmin(actorId);

        // 2. 规范化后的条件同时用于 count 和 list，保证分页元数据与结果一致。
        String normalizedStatus = normalize(status);
        String normalizedKeyword = normalize(keyword);
        int currentPage = page(page);
        int pageSize = size(size);

        // 3. totalCount 表示所有匹配房源，offset 只决定当前页从哪条记录开始读取。
        long totalCount = mapper.countHouses(normalizedStatus, normalizedKeyword);
        int offset = (currentPage - 1) * pageSize;
        List<AdminHouseView> results = mapper
                .findHouses(normalizedStatus, normalizedKeyword, offset, pageSize)
                .stream()
                .map(AdminHouseView::from)
                .toList();

        return page(totalCount, currentPage, pageSize, "/api/admin/houses/", results);
    }

    /**
     * 汇总后台首页所需的用户、房源与交易统计。
     *
     * @param actorId 操作人编号
     */
    public Map<String, Object> dashboard(Long actorId) {
        requireAdmin(actorId);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> house = new LinkedHashMap<>();
        house.put("total", count("SELECT COUNT(*) FROM house"));
        for (String status : List.of("approved", "pending", "rejected", "offline")) {
            house.put(status, count("SELECT COUNT(*) FROM house WHERE status=?", status));
        }
        house.put("whole", count("SELECT COUNT(*) FROM house WHERE house_type='whole'"));
        house.put("share", count("SELECT COUNT(*) FROM house WHERE house_type='share'"));
        house.put("avg_price", decimal("SELECT COALESCE(AVG(price),0) FROM house WHERE status='approved'"));
        house.put("avg_area", decimal("SELECT COALESCE(AVG(area),0) FROM house WHERE status='approved'"));
        result.put("houses", house);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("total", count("SELECT COUNT(*) FROM user_profile"));
        for (String role : ROLES) {
            user.put(role, count("SELECT COUNT(*) FROM user_profile WHERE role=?", role));
        }
        result.put("users", user);
        result.put("area_distribution", jdbc.query("""
                SELECT COALESCE(r.name,'未知') name,COUNT(*) count FROM house h
                LEFT JOIN area r ON r.id=h.region_id WHERE h.status='approved'
                GROUP BY r.name ORDER BY count DESC LIMIT 10
                """, (rs, n) -> Map.of("name", rs.getString("name"), "count", rs.getLong("count"))));
        result.put("price_distribution", priceDistribution());
        result.put("recent_trend", recentTrend());

        BigDecimal publish = decimal("SELECT COALESCE(SUM(amount),0) FROM publish_record WHERE is_paid=TRUE");
        BigDecimal recommend = decimal("SELECT COALESCE(SUM(amount),0) FROM point_purchase_record");
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime month = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Map<String, Object> income = new LinkedHashMap<>();
        income.put("publish", publish);
        income.put("recommend", recommend);
        income.put("total", publish.add(recommend));
        income.put("today", incomeSince(today));
        income.put("month", incomeSince(month));
        result.put("income", income);
        return result;
    }

    /**
     * 统计各租金区间的房源数量分布。
     */
    private List<Map<String, Object>> priceDistribution() {
        int[][] ranges = {{0, 1000}, {1000, 2000}, {2000, 3000}, {3000, 5000}, {5000, 10000}};
        String[] labels = {"1000以下", "1000-2000", "2000-3000", "3000-5000", "5000-10000"};
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < ranges.length; index++) {
            result.add(Map.of("label", labels[index], "count",
                    count("SELECT COUNT(*) FROM house WHERE price>=? AND price<?",
                            ranges[index][0], ranges[index][1])));
        }
        result.add(Map.of("label", "10000以上", "count", count("SELECT COUNT(*) FROM house WHERE price>=10000")));
        return result;
    }

    /**
     * 统计最近一段时间的房源发布趋势。
     */
    private List<Map<String, Object>> recentTrend() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int days = 6; days >= 0; days--) {
            LocalDate date = LocalDate.now().minusDays(days);
            result.add(Map.of("date", "%02d-%02d".formatted(date.getMonthValue(), date.getDayOfMonth()),
                    "houses", count("SELECT COUNT(*) FROM house WHERE create_time>=? AND create_time<?",
                            date.atStartOfDay(), date.plusDays(1).atStartOfDay()),
                    "users", count("SELECT COUNT(*) FROM auth_user WHERE date_joined>=? AND date_joined<?",
                            date.atStartOfDay(), date.plusDays(1).atStartOfDay())));
        }
        return result;
    }

    /**
     * 统计指定时间之后的平台收入。
     *
     * @param time 时间
     */
    private BigDecimal incomeSince(LocalDateTime time) {
        return decimal("SELECT COALESCE(SUM(amount),0) FROM publish_record WHERE is_paid=TRUE AND created_at>=?", time)
                .add(decimal("SELECT COALESCE(SUM(amount),0) FROM point_purchase_record WHERE created_at>=?", time));
    }
    /**
     * 将数据库统计结果安全转换为整数。
     *
     * @param sql 统计查询语句
     * @param args SQL 绑定参数
     * @return 符合条件的数据数量
     */
    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
    /**
     * 将输入值转换为金额类型。
     *
     * @param sql 统计查询语句
     * @param args SQL 绑定参数
     */
    private BigDecimal decimal(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
    /**
     * 查询用户资料并校验管理员权限。
     *
     * @param id 编号
     */
    private void requireAdmin(Long id) {
        UserProfile p = users.findProfileByUserId(id);
        if (p == null || !"admin".equals(p.role())) {
            throw new BusinessException("ADMIN_REQUIRED", "需要管理员权限", HttpStatus.FORBIDDEN);
        }
    }
    /**
     * 校验并规范化用户角色。
     *
     * @param role 角色
     */
    private String role(String role) {
        if (!ROLES.contains(role)) {
            invalid("无效角色");
        }
        return role;
    }
    /**
     * 校验必填文本并返回去除首尾空白后的值。
     *
     * @param value 字段值
     * @param message 消息
     */
    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            invalid(message);
        }
        return value.trim();
    }
    /**
     * 规范化输入值，避免空值影响后续处理。
     *
     * @param value 字段值
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    /**
     * 将页码限制在有效范围内。
     *
     * @param value 字段值
     */
    private int page(int value) {
        return Math.max(1, value);
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
     * 创建参数校验失败类型的业务异常。
     *
     * @param message 消息
     */
    private void invalid(String message) {
        throw new BusinessException("INVALID_ADMIN_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
    /**
     * 创建资源不存在类型的业务异常。
     *
     * @param message 消息
     */
    private void notFound(String message) {
        throw new BusinessException("ADMIN_RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
    /**
     * 将页码限制在有效范围内。
     *
     * @param count 数量
     * @param page 页码
     * @param size 每页数量
     * @param path 资源路径
     * @param values 待处理的数据集合
     */
    private <T> PageResponse<T> page(long totalCount, int currentPage, int pageSize,
                                     String path, List<T> results) {
        String next = (long) currentPage * pageSize < totalCount
                ? path + "?page=" + (currentPage + 1)
                : null;
        String previous = currentPage > 1
                ? path + "?page=" + (currentPage - 1)
                : null;
        return new PageResponse<>(totalCount, next, previous, results);
    }
}
