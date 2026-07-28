package com.bulongyu.housing.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 从 Spring Security 的 JWT 认证信息中解析当前登录用户编号。 */
@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 判断控制器参数是否由当前解析器处理。
     *
     * @param parameter 控制器方法参数
     * @return 参数使用 {@link CurrentUserId} 且类型为 {@link Long} 时返回 {@code true}
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    /**
     * 解析当前登录用户编号。
     *
     * @param parameter 控制器方法参数
     * @param mavContainer 模型与视图容器
     * @param webRequest 当前 Web 请求
     * @param binderFactory 数据绑定工厂
     * @return 当前登录用户编号；可选认证接口未登录时返回 {@code null}
     * @throws AuthenticationCredentialsNotFoundException 必需认证接口未找到有效认证信息时抛出
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt) {
            return Long.valueOf(jwt.getSubject());
        }

        // 公开接口可以在匿名请求中显式接收 null，其他接口必须具备有效 JWT 认证。
        CurrentUserId annotation = parameter.getParameterAnnotation(CurrentUserId.class);
        if (annotation != null && !annotation.required()) {
            return null;
        }
        throw new AuthenticationCredentialsNotFoundException("未找到当前登录用户认证信息");
    }
}
