package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.dto.RefreshRequest;
import com.bulongyu.housing.vo.RefreshResponse;

import com.bulongyu.housing.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证接口控制器
 */
@RestController
@RequestMapping("/api/token")
public class TokenController {
    private final AuthService authService;

    /**
     * 初始化 {@code TokenController} 并注入所需依赖。
     *
     * @param authService 用户认证服务
     */
    public TokenController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 校验刷新令牌并签发新的令牌对。
     *
     * @param request 请求参数
     */
    @PostMapping("/refresh/")
    RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refresh());
    }
}
