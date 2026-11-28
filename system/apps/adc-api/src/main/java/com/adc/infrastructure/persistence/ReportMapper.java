package com.adc.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 报表查询（UC-08 / UC-09）。只读，不参与业务写入。
 * <p>
 * 两条口径务必分清：
 * <ul>
 *   <li><b>预约侧</b>：取消率 = （未到诊 + 已取消）/（已就诊 + 未到诊 + 已取消），待就诊不进分母；</li>
 *   <li><b>收入侧</b>：只认状态为已出具/待支付/已结算的结算单，已红冲的原单不计，
 *       红字负额单按“红冲指向的原单”归到原诊所/原牙医，两单自然抵销。</li>
 * </ul>
 */
@Mapper
public interface ReportMapper {

    List<java.util.Map<String, Object>> cancellationByClinic(@Param("from") LocalDate from,
                                                             @Param("to") LocalDate to,
                                                             @Param("clinicId") Integer clinicId);

    List<java.util.Map<String, Object>> cancellationByChannel(@Param("from") LocalDate from,
                                                              @Param("to") LocalDate to,
                                                              @Param("clinicId") Integer clinicId);

    List<java.util.Map<String, Object>> quarterByDentist(@Param("start") LocalDate start,
                                                         @Param("end") LocalDate end);
}
