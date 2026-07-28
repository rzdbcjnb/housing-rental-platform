package com.bulongyu.housing.service;

import com.bulongyu.housing.dto.LoginRequest;
import com.bulongyu.housing.dto.RegisterRequest;
import com.bulongyu.housing.dto.UpdateProfileRequest;
import com.bulongyu.housing.vo.AuthPayload;
import com.bulongyu.housing.vo.AuthResponse;
import com.bulongyu.housing.vo.AuthUserView;
import com.bulongyu.housing.vo.RefreshResponse;
import com.bulongyu.housing.vo.TokenPair;
import com.bulongyu.housing.vo.UniqueResponse;
import com.bulongyu.housing.vo.UpdateProfileResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.AuthUser;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import com.bulongyu.housing.security.DjangoPbkdf2PasswordEncoder;
import com.bulongyu.housing.security.JwtService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户认证业务服务
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Set<String> PUBLIC_ROLES = Set.of("tenant", "landlord");

    private final UserMapper userMapper;
    private final DjangoPbkdf2PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * 初始化 {@code AuthService} 并注入所需依赖。
     *
     * @param userMapper 用户数据访问组件
     * @param passwordEncoder 密码编码器
     * @param jwtService 认证令牌服务
     */
    public AuthService(UserMapper userMapper, DjangoPbkdf2PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 注册用户并创建对应用户资料。
     *
     * @param request 请求参数
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("注册用户，参数：username={}，role={}", request.username(), request.role());
        String role = request.role() == null ? "tenant" : request.role();
        if (!PUBLIC_ROLES.contains(role)) {
            throw new BusinessException("INVALID_ROLE", "角色必须为 tenant 或 landlord", HttpStatus.BAD_REQUEST);
        }
        // 1. 先做角色与唯一性校验，减少进入写事务后才失败的无效操作。
        ensureUsernameAvailable(request.username(), null);
        ensurePhoneAvailable(request.phone(), null);
        LocalDateTime now = LocalDateTime.now();
        try {
            // 2. 先写认证账号，再用生成的用户编号写资料；任一步失败时由事务整体回滚。
            // 应用层预检提供友好错误，数据库唯一约束负责拦截并发注册。
            userMapper.insertUser(request.username(), passwordEncoder.encode(request.password()), now);
            AuthUser user = requireUserByUsername(request.username());
            userMapper.insertProfile(user.id(), request.phone(), role, now);
            UserProfile profile = requireProfile(user.id());
            return authResponse("注册成功", user, profile);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("USER_CONFLICT", "用户名或手机号已存在", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 校验账号密码并签发访问令牌与刷新令牌。
     *
     * @param request 请求参数
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("用户登录，参数：username={}", request.username());
        // 只比较密码摘要，不记录密码或令牌；失败时统一返回凭据错误，避免暴露账号是否存在。
        AuthUser user = userMapper.findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.password())) {
            throw new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        if (!user.active()) {
            throw new BusinessException("ACCOUNT_DISABLED", "账号已被禁用", HttpStatus.FORBIDDEN);
        }
        UserProfile profile = requireProfile(user.id());
        // 最近登录时间更新与本次登录事务共同提交，令牌仍由专用 JwtService 负责签发。
        userMapper.updateLastLogin(user.id(), LocalDateTime.now());
        return authResponse("登录成功", user, profile);
    }

    /**
     * 查询当前登录用户的资料。
     *
     * @param userId 用户编号
     * @return 用户信息
     */
    public AuthUserView getUserInfo(Long userId) {
        AuthUser user = requireActiveUser(userId);
        return toView(user, requireProfile(userId));
    }

    /**
     * 更新当前登录用户的个人资料。
     *
     * @param userId 用户编号
     * @param request 请求参数
     */
    @Transactional
    public UpdateProfileResponse updateUserInfo(Long userId, UpdateProfileRequest request) {
        AuthUser user = requireActiveUser(userId);
        UserProfile profile = requireProfile(userId);
        String username = hasText(request.username()) ? request.username().trim() : user.username();
        String phone = hasText(request.phone()) ? request.phone().trim() : profile.phone();
        String avatar = hasText(request.avatar()) ? request.avatar().trim() : profile.avatar();
        ensureUsernameAvailable(username, user.id());
        ensurePhoneAvailable(phone, user.id());
        try {
            // 用户名和资料更新共享同一事务；后一步冲突时前一步也会回滚。
            if (!username.equals(user.username())) {
                userMapper.updateUsername(userId, username);
            }
            userMapper.updateProfile(userId, phone, avatar, LocalDateTime.now());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("USER_CONFLICT", "用户名或手机号已存在", HttpStatus.BAD_REQUEST);
        }
        AuthUser updatedUser = requireUserByUsername(username);
        return new UpdateProfileResponse("更新成功", toView(updatedUser, requireProfile(userId)));
    }

    /**
     * 检查用户名或手机号是否已被占用。
     *
     * @param field 约束字段
     * @param value 字段值
     */
    public UniqueResponse checkUnique(String field, String value) {
        log.info("检查用户字段唯一性，参数：field={}", field);
        if (!hasText(field) || !hasText(value)) {
            throw new BusinessException("MISSING_PARAMETER", "缺少必要参数", HttpStatus.BAD_REQUEST);
        }
        boolean exists;
        String message;
        if ("username".equals(field)) {
            exists = userMapper.countByUsername(value) > 0;
            message = exists ? "用户名已存在" : "用户名可用";
        } else if ("phone".equals(field)) {
            exists = userMapper.countByPhone(value) > 0;
            message = exists ? "手机号已存在" : "手机号可用";
        } else {
            throw new BusinessException("UNSUPPORTED_FIELD", "不支持的检查字段", HttpStatus.BAD_REQUEST);
        }
        return new UniqueResponse(field, value, exists, message);
    }

    /**
     * 校验刷新令牌并签发新的令牌对。
     *
     * @param refreshToken refresh令牌
     */
    public RefreshResponse refresh(String refreshToken) {
        try {
            Jwt jwt = jwtService.decodeRefreshToken(refreshToken);
            Long userId = Long.valueOf(jwt.getSubject());
            AuthUser user = requireActiveUser(userId);
            TokenPair pair = jwtService.issueTokenPair(user, requireProfile(userId));
            return new RefreshResponse(pair.access());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "刷新令牌无效", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * 组装用户信息和令牌对并生成认证响应。
     *
     * @param message 消息
     * @param user 用户
     * @param profile 用户资料
     */
    private AuthResponse authResponse(String message, AuthUser user, UserProfile profile) {
        return new AuthResponse(message,
                new AuthPayload(toView(user, profile), jwtService.issueTokenPair(user, profile)));
    }

    /**
     * 将持久化对象转换为接口返回模型。
     *
     * @param user 用户
     * @param profile 用户资料
     */
    private AuthUserView toView(AuthUser user, UserProfile profile) {
        return new AuthUserView(user.id(), user.username(), profile.phone(),
                profile.role(), profile.avatar());
    }

    /**
     * 查询并校验是否启用用户。
     *
     * @param userId 用户编号
     */
    private AuthUser requireActiveUser(Long userId) {
        AuthUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        if (!user.active()) {
            throw new BusinessException("ACCOUNT_DISABLED", "账号已被禁用", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    /**
     * 查询并校验用户用户名。
     *
     * @param username 用户名
     */
    private AuthUser requireUserByUsername(String username) {
        AuthUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        return user;
    }

    /**
     * 查询并校验用户资料。
     *
     * @param userId 用户编号
     */
    private UserProfile requireProfile(Long userId) {
        UserProfile profile = userMapper.findProfileByUserId(userId);
        if (profile == null) {
            throw new BusinessException("PROFILE_NOT_FOUND", "用户资料不存在", HttpStatus.NOT_FOUND);
        }
        return profile;
    }

    /**
     * 校验用户名未被其他用户占用。
     *
     * @param username 用户名
     * @param currentUserId 当前用户编号
     */
    private void ensureUsernameAvailable(String username, Long currentUserId) {
        AuthUser existing = userMapper.findByUsername(username);
        if (existing != null && !existing.id().equals(currentUserId)) {
            throw new BusinessException("USERNAME_EXISTS", "用户名已存在", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 校验手机号未被其他用户占用。
     *
     * @param phone 手机号
     * @param currentUserId 当前用户编号
     */
    private void ensurePhoneAvailable(String phone, Long currentUserId) {
        if (userMapper.countByPhone(phone) == 0) {
            return;
        }
        if (currentUserId != null) {
            UserProfile current = userMapper.findProfileByUserId(currentUserId);
            if (current != null && phone.equals(current.phone())) {
                return;
            }
        }
        throw new BusinessException("PHONE_EXISTS", "手机号已存在", HttpStatus.BAD_REQUEST);
    }

    /**
     * 判断是否包含文本。
     *
     * @param value 字段值
     * @return 条件成立时返回 true，否则返回 false
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
