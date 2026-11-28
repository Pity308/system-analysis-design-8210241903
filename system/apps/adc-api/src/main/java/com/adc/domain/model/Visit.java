package com.adc.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.adc.common.ApiException;

import lombok.Data;

/**
 * 就诊记录（报告 6.5 分析类图中的「就诊」聚合根）。
 * <p>
 * 报告 UC-03 的业务规则落在这里：
 * <ul>
 *   <li>治疗行只有在牙医确认后才计入本次就诊；</li>
 *   <li>就诊记录一经生成，治疗行不得物理删除，更正走红冲；</li>
 *   <li>散客（walk-in）不补造预约，{@code appointmentId} 留空。</li>
 * </ul>
 */
@Data
public class Visit {

    public static final String IN_PROGRESS = "进行中";
    public static final String COMPLETED   = "已完成";

    private Integer visitId;
    private Integer appointmentId;
    private Integer patientId;
    private Integer employeeId;
    private Integer clinicId;

    private LocalDateTime arrivalTime;
    private LocalDateTime endTime;
    private LocalDateTime recordedTime;

    private String chiefComplaint;
    private String diagnosis;
    private String status;

    /** 联表带出的可读信息，只用于呈现。 */
    private String patientName;
    private String patientPhone;
    private String dentistName;
    private String clinicName;
    private String clinicCode;

    /** 已开出的结算单号；null 表示还没开单（前台据此决定“开单”还是“调单”）。 */
    private String statementNo;
    private String statementStatus;

    private List<VisitTreatment> treatments = new ArrayList<>();

    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }

    /** 确认就诊：治疗行冻结，后续更正只能红冲，不能再改。 */
    public void complete(LocalDateTime now) {
        if (isCompleted()) {
            throw ApiException.unprocessable("该就诊记录已完成，不能重复确认");
        }
        if (treatments.isEmpty()) {
            throw ApiException.unprocessable("没有治疗行，不能确认就诊（至少录入一项治疗）");
        }
        this.status = COMPLETED;
        this.endTime = now;
    }

    public void requireEditable() {
        if (isCompleted()) {
            throw ApiException.unprocessable(
                    "就诊记录已完成，治疗行不得修改；如需更正请开具红字结算单留痕");
        }
    }
}
