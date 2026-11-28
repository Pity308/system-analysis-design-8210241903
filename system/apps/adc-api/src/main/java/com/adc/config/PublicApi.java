package com.adc.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 不需要登录的接口：登录、健康检查、以及员工端与小程序共用的参考数据
 * （诊所、治疗项目、过敏原、可约时段）。
 * <p>
 * 参考数据之所以放行：诊所地址电话与价目表本来就要给患者看，
 * 挡住反而逼着小程序另开一套接口；而它们不含任何个人数据。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
