package com.adc.web.dto;

import java.time.LocalDateTime;

/**
 * UC-03 开始就诊入参。
 * <p>
 * 两种来源共用同一个入口：
 * <ul>
 *   <li>有预约：只传 {@code appointmentId}，患者/牙医/诊所一律以预约为准；</li>
 *   <li>散客：不传预约，改传 {@code patientId + employeeId + clinicId}。</li>
 * </ul>
 */
public record VisitStartRequest(
        Integer appointmentId,
        Integer patientId,
        Integer employeeId,
        Integer clinicId,
        String chiefComplaint,
        String diagnosis,
        LocalDateTime arrivalTime
) {
}
