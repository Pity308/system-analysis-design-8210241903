package com.adc.domain.model;

import java.time.LocalDateTime;

import com.adc.common.ApiException;

import lombok.Data;

/**
 * 预约聚合根（报告 6.5 分析类图中的「预约」）。
 * <p>
 * 状态改写规则（报告 UC-02 业务规则第 6 条）收在这个对象里，业务代码只能通过下列方法改状态；
 * Lombok 生成的 {@code setStatus} 仅供 ORM 回填使用，控制类与边界类不得调用：
 * <ul>
 *   <li>“待就诊” → “已就诊” / “未到诊”：由 UC-05 触发；</li>
 *   <li>“待就诊” → “已取消”：仅当当前时间早于预约时间。</li>
 * </ul>
 * 已就诊只进取消率的分母，未到诊与已取消同时进分子，所以三者绝不能混用。
 */
@Data
public class Appointment {

    public static final String PENDING   = "待就诊";
    public static final String ATTENDED  = "已就诊";
    public static final String NO_SHOW   = "未到诊";
    public static final String CANCELLED = "已取消";

    private Integer appointmentId;
    private String appointmentNo;
    private Integer patientId;
    private Integer employeeId;
    private Integer clinicId;
    private LocalDateTime appointmentTime;
    private Integer expectedMinutes;
    private String channel;
    private String status;
    private LocalDateTime createdTime;
    private String cancelReason;

    /** 就诊备注（报告 UC-02 字段列表中的可选项）。 */
    private String note;

    /** 治疗需求项目编号，来源 appointment_treatment；只用于呈现与资质校验。 */
    private java.util.List<Integer> treatmentItemIds = new java.util.ArrayList<>();

    /** 联表带出的可读信息，只用于呈现。 */
    private String patientName;
    private String patientPhone;
    private String dentistName;
    private String clinicName;
    private String clinicCode;

    public LocalDateTime endTime() {
        return appointmentTime.plusMinutes(expectedMinutes);
    }

    public void markAttended() {
        requireStatus(PENDING, "只有“待就诊”的预约可以标记为已就诊");
        this.status = ATTENDED;
    }

    public void markNoShow() {
        requireStatus(PENDING, "只有“待就诊”的预约可以标记为未到诊");
        this.status = NO_SHOW;
    }

    /** 取消：晚于预约时间的“爽约”应记为未到诊，不能事后改成已取消。 */
    public void cancel(String reason, LocalDateTime now) {
        requireStatus(PENDING, "只有“待就诊”的预约可以取消");
        if (!now.isBefore(appointmentTime)) {
            throw ApiException.unprocessable("预约时间已过，不能再取消；请按“未到诊”登记");
        }
        this.status = CANCELLED;
        this.cancelReason = reason;
    }

    private void requireStatus(String expected, String message) {
        if (!expected.equals(this.status)) {
            throw ApiException.unprocessable(message + "（当前状态：" + this.status + "）");
        }
    }
}
