package com.adc.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.adc.domain.model.TreatmentItem;

/**
 * 治疗项目数据映射器。UC-02 用它判断资质要求，UC-03/UC-04 用它取价目快照。
 */
@Mapper
public interface TreatmentItemMapper {

    List<TreatmentItem> findActive();

    TreatmentItem findById(@Param("itemId") Integer itemId);

    /** 全连锁是否有任何牙医持有该资质——用于区分“无人有资质”与“该时段没人上班”。 */
    int countDentistsWithQualification(@Param("qualificationId") Integer qualificationId);
}
