package com.adc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ADC 牙科连锁信息管理系统 —— 后端入口。
 *
 * <p>分层约定（对应报告 6.4 / 6.13）：
 * <ul>
 *   <li>{@code domain}：实体与领域操作，规则的家，不认识 SQL；</li>
 *   <li>{@code application}：控制类，编排流程、划定事务边界；</li>
 *   <li>{@code infrastructure}：数据映射器，负责把领域对象存进 PostgreSQL；</li>
 *   <li>{@code web}：边界类，只做输入采集与输出呈现，不做业务判断。</li>
 * </ul>
 */
@SpringBootApplication
public class AdcApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdcApplication.class, args);
    }
}
