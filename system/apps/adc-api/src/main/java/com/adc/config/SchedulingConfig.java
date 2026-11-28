package com.adc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 打开定时任务。
 *
 * <p>报告图 14 说明里那句“后台批处理不占窗体，由时间触发”，落到代码就是这里：
 * 逾期未到诊判定不需要人点按钮，每天固定时间跑一次。
 * 单独一个配置类而不是写在启动类上，是为了让“系统里有几处按时间跑的东西”一眼可见。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
