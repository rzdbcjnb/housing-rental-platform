package com.bulongyu.housing.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要注入当前登录用户编号的控制器参数。 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {

    /**
     * 是否要求请求中存在已认证用户。
     * {@code true} 表示接口必须存在已认证用户；{@code false} 表示公开接口未登录时允许注入 {@code null}。
     */
    boolean required() default true;
}
