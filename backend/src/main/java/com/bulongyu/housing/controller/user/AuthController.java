package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.security.CurrentUserId;

import com.bulongyu.housing.dto.LoginRequest;
import com.bulongyu.housing.dto.RegisterRequest;
import com.bulongyu.housing.dto.UpdateProfileRequest;
import com.bulongyu.housing.vo.AuthResponse;
import com.bulongyu.housing.vo.AuthUserView;
import com.bulongyu.housing.vo.UniqueResponse;
import com.bulongyu.housing.vo.UpdateProfileResponse;

import com.bulongyu.housing.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证接口控制器
 */
@RestController
@RequestMapping("/api/users")
public class AuthController {
    private final AuthService authService;

    /**
     * 初始化 {@code AuthController} 并注入所需依赖。
     *
     * @param authService 用户认证服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册用户并创建对应用户资料。
     *
     * @param request 请求参数
     */
    @PostMapping("/register/")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * 校验账号密码并签发访问令牌与刷新令牌。
     *
     * @param request 请求参数
     */
    @PostMapping("/login/")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * 查询当前登录用户资料。
     *
     * @param currentUserId 当前登录用户编号
     */
    @GetMapping("/info/")
    AuthUserView info(@CurrentUserId Long currentUserId) {
        return authService.getUserInfo(currentUserId);
    }

    /**
     * 更新信息。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     */
    @PutMapping("/info/")
    UpdateProfileResponse updateInfo(@CurrentUserId Long currentUserId,
                                         @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateUserInfo(currentUserId, request);
    }

    /**
     * 检查用户名或手机号是否已被占用。
     *
     * @param field 约束字段
     * @param value 字段值
     */
    @GetMapping("/check-unique/")
    UniqueResponse checkUnique(@RequestParam(required = false) String field,
                                          @RequestParam(required = false) String value) {
        return authService.checkUnique(field, value);
    }
}
