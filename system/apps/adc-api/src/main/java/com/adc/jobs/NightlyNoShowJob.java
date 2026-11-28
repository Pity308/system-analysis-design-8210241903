package com.adc.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.adc.application.AppointmentService;
import com.adc.application.dto.SweepResult;

/**
 * 每日逾期未到诊判定（UC-05 的后台批处理）。
 *
 * <p>为什么要有：取消率的分子是“未到诊＋已取消”，分母再加上“已就诊”。
 * 患者没来、前台又忘了点“未到诊”，这条预约就停在“待就诊”，两头都不算——
 * 它会从统计里凭空消失，取消率被系统性低估，季度激励基金的核算跟着失真。
 * 人不可靠，所以要有一次“过了时段就判未到诊”的机器兜底。
 *
 * <p>三个设计细节：
 * <ul>
 *   <li><b>宽限 15 分钟</b>：迟到是常态，不能一到点就判爽约；</li>
 *   <li><b>按预计时长顺延</b>：判定条件是“预约时间 + 预计时长 + 宽限”，
 *       60 分钟的洁牙在开始后 30 分钟仍在治疗中，不该被判未到诊；</li>
 *   <li><b>不覆盖已开就诊的</b>：有就诊记录的患者走“已就诊”，
 *       这条由 {@code status = 待就诊} 的条件天然保证。</li>
 * </ul>
 *
 * <p>手动补跑走 {@code POST /api/appointments/sweep}，前台的到诊登记页有按钮。
 */
@Component
public class NightlyNoShowJob {

    private static final Logger log = LoggerFactory.getLogger(NightlyNoShowJob.class);

    /** 迟到宽限：与前台手动补跑的默认值保持一致，避免两个入口判出不同结果。 */
    private static final int GRACE_MINUTES = 15;

    private final AppointmentService appointments;

    /** 关掉它是为了在演示环境里不让定时任务改数据（配置项，不是代码开关）。 */
    private final boolean enabled;

    public NightlyNoShowJob(AppointmentService appointments,
                            @Value("${adc.jobs.no-show-enabled:true}") boolean enabled) {
        this.appointments = appointments;
        this.enabled = enabled;
    }

    /** 每天 00:05（Asia/Shanghai）。cron 可配，时区显式指定，不依赖服务器本地时区。 */
    @Scheduled(cron = "${adc.jobs.no-show-cron:0 5 0 * * *}", zone = "Asia/Shanghai")
    public void sweep() {
        if (!enabled) {
            log.info("逾期未到诊判定已关闭（adc.jobs.no-show-enabled=false），跳过");
            return;
        }
        SweepResult r = appointments.markOverdueAsNoShow(null, GRACE_MINUTES);
        if (r.affected() == 0) {
            log.info("逾期未到诊判定：无逾期记录（宽限 {} 分钟）", r.graceMinutes());
        } else {
            log.info("逾期未到诊判定：{} 条预约置为未到诊（宽限 {} 分钟），已写入审计",
                    r.affected(), r.graceMinutes());
        }
    }
}
