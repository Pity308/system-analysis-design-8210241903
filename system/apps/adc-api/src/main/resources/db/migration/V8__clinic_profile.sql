-- =============================================================
-- UC-07 维护诊所信息：补齐规约要求的三个属性
--   报告 UC-07 的字段清单是「地址、主管姓名、电话、邮箱、办公面积」，
--   V1 建表时只落了地址与电话，这里补上另外三个，并同步回写报告的
--   数据字典与建表脚本（6.3 节 clinic 表）。
-- =============================================================

ALTER TABLE clinic ADD COLUMN manager_name VARCHAR(30);
ALTER TABLE clinic ADD COLUMN email        VARCHAR(50);
ALTER TABLE clinic ADD COLUMN area_sqm     NUMERIC(6, 1);

ALTER TABLE clinic
  ADD CONSTRAINT ck_clinic_area CHECK (area_sqm IS NULL OR area_sqm > 0);

COMMENT ON COLUMN clinic.manager_name IS '主管姓名（诊所主管，岗位而非员工上级）';
COMMENT ON COLUMN clinic.email        IS '诊所邮箱';
COMMENT ON COLUMN clinic.area_sqm     IS '办公面积（平方米）';

-- 种子数据：邮箱按诊所编号推导，面积为诊椅数折算，主管姓名按诊所固定指派
UPDATE clinic SET
  email        = 'clinic' || lpad(clinic_id::text, 2, '0') || '@adc-dental.cn',
  area_sqm     = chair_count * 58 + (clinic_id % 3) * 6,
  manager_name = (ARRAY['梁志明', '何淑芬', '邓国华', '罗嘉俊', '谭雅琴',
                        '曾繁荣', '孔祥辉', '麦丽珍', '叶伟强', '钟惠芳',
                        '冯少波', '岑家豪', '温子谦', '古丽娜', '区文杰'])[clinic_id];
