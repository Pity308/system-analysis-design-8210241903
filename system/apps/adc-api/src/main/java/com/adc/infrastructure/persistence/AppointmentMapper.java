package com.adc.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.adc.domain.model.Appointment;
import com.adc.domain.model.BusyInterval;
import com.adc.domain.model.DentistShift;

/**
 * 预约与排班的数据映射器（UC-02 / UC-05 共用）。
 * <p>
 * 排班与已占区间都是只读查询；预约为写入。业务判断一律不在这里做。
 */
@Mapper
public interface AppointmentMapper {

    /**
     * 取某天可用的排班（含资质附列）。
     *
     * @param clinicId 为空表示不限诊所
     */
    List<DentistShift> findShifts(@Param("date") LocalDate date,
                                  @Param("clinicId") Integer clinicId);

    /** 某天该批牙医已被占用的时间区间（已取消的不算占用）。 */
    List<BusyInterval> findBusy(@Param("employeeIds") List<Integer> employeeIds,
                                @Param("dayStart") LocalDateTime dayStart,
                                @Param("dayEnd") LocalDateTime dayEnd);

    /** 该牙医当天是否有覆盖 [start, end] 的排班——提交时校验，防止绕过可约时段直接下单。 */
    DentistShift findShiftCovering(@Param("employeeId") Integer employeeId,
                                   @Param("date") LocalDate date,
                                   @Param("clinicId") Integer clinicId,
                                   @Param("startTime") LocalTime startTime,
                                   @Param("endTime") LocalTime endTime);

    /** 牙医持有的资质编号，提交时二次校验（可约时段列表只是“推荐”，不能当作鉴权）。 */
    List<Integer> findQualificationIds(@Param("employeeId") Integer employeeId);

    /** 单号流水：按“诊所 + 日期”取当日已用最大流水号，配合事务内咨询锁防并发重号。 */
    Integer maxSerial(@Param("prefix") String prefix);

    /** 事务级咨询锁（同诊所同日串行生成流水号），返回值本身无意义。 */
    Integer lockSerial(@Param("key") String key);
    int insert(Appointment appointment);

    void insertTreatment(@Param("appointmentId") Integer appointmentId,
                         @Param("itemId") Integer itemId);

    List<Integer> findTreatmentItemIds(@Param("appointmentId") Integer appointmentId);

    /** 按日期 + 诊所 / 牙医 / 状态取列表（到诊登记与牙医待就诊列表共用）。 */
    List<Appointment> findByFilter(@Param("dayStart") LocalDateTime dayStart,
                                   @Param("dayEnd") LocalDateTime dayEnd,
                                   @Param("clinicId") Integer clinicId,
                                   @Param("employeeId") Integer employeeId,
                                   @Param("status") String status);

    Appointment findById(@Param("appointmentId") Integer appointmentId);

    /**
     * 逾期仍停在“待就诊”的预约（UC-05 后台批处理的判定依据）。
     * <p>
     * 判定用 {@code 预约时间 + 预计时长 + 宽限 < now}，而不是只比预约时间：
     * 一位 10:00 开始、预计 60 分钟的患者，10:30 就被判未到诊是错的。
     */
    List<Appointment> findOverdue(@Param("now") LocalDateTime now,
                                  @Param("graceMinutes") int graceMinutes,
                                  @Param("clinicId") Integer clinicId);

    Appointment findByNo(@Param("appointmentNo") String appointmentNo);

    /** 患者近 N 天未到诊次数，用于 UC-02 规则 3 的提示（不阻断）。 */
    int countNoShowSince(@Param("patientId") Integer patientId,
                         @Param("since") LocalDateTime since);

    /** UC-05 状态改写。 */
    int updateStatus(@Param("appointmentId") Integer appointmentId,
                     @Param("status") String status,
                     @Param("cancelReason") String cancelReason);

    /**
     * 患者本人的预约（UC-10 第 7 步“查看自己的预约”）。
     * <p>
     * 只按 patient_id 过滤，不接收前端传入的任意条件；调用方必须已经用 openid 换出 patientId。
     */
    List<Appointment> findByPatient(@Param("patientId") Integer patientId,
                                    @Param("from") LocalDateTime from);
}
