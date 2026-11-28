package com.adc.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明某个边界类或方法允许哪些角色调用。
 * <p>
 * 写在类上表示该类所有方法同一口径，写在方法上表示单独收紧（方法优先）。
 * 不做“默认全放开、要权限才标注”，而是“默认要求登录、要具体角色才标注”——
 * 少标一处只会少一层限制，不会多开一个口子。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** RECEPTIONIST / DENTIST / ADMIN，见 UserAccount 的常量。 */
    String[] value();
}
