package com.adc.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.adc.domain.model.Visit;
import com.adc.domain.model.VisitTreatment;

/**
 * 就诊数据映射器（UC-03）。
 */
@Mapper
public interface VisitMapper {

    Visit findById(@Param("visitId") Integer visitId);

    Visit findByAppointment(@Param("appointmentId") Integer appointmentId);

    /** 牙医工作台的当日就诊列表。 */
    List<Visit> findByDentistAndDate(@Param("employeeId") Integer employeeId,
                                     @Param("dayStart") LocalDateTime dayStart,
                                     @Param("dayEnd") LocalDateTime dayEnd);

    /** 某天某诊所的就诊列表（前台开单入口），带是否已开单。 */
    List<Visit> findByDay(@Param("dayStart") LocalDateTime dayStart,
                          @Param("dayEnd") LocalDateTime dayEnd,
                          @Param("clinicId") Integer clinicId);

    List<VisitTreatment> findTreatments(@Param("visitId") Integer visitId);

    int insert(Visit visit);

    int insertTreatment(VisitTreatment t);

    int complete(@Param("visitId") Integer visitId, @Param("endTime") LocalDateTime endTime);

    int updateComplaint(@Param("visitId") Integer visitId,
                        @Param("chiefComplaint") String chiefComplaint,
                        @Param("diagnosis") String diagnosis);

    /** 患者是否还有未结清的结算单（UC-04 规则 6 的提示依据）。 */
    int countUnsettledStatements(@Param("patientId") Integer patientId);

    LocalDate findArrivalDate(@Param("visitId") Integer visitId);
}
