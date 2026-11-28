-- 滚动可约窗口配套：给诊所1铺一整年的排班。
--
-- 背景：可约窗口已从"固定 90 天"改为"当天起一个月"（AppointmentService.plusMonths(1)）。
-- V11 只铺到 2026-12-28，之后就没班了。本迁移以部署当天的 CURRENT_DATE 为起点铺一年，
-- 保证 12/2 登录能约到 1/2、1/15 登录能约到 2/15……任何一天登录都有整整一个月的班。
--
-- 牙医1（主任医师，资质1、2）与牙医3（主治医师，3 项资质），诊所1每天 09:00–18:00。
-- ON CONFLICT DO NOTHING：与 V11 已铺的排班不冲突，可重复执行。
-- 一年 ≈ 366 天 × 2 牙医 ≈ 732 行，数据量极小。

INSERT INTO dentist_schedule (employee_id, clinic_id, work_date, start_time, end_time)
SELECT d.employee_id, 1, g.work_date, TIME '09:00:00', TIME '18:00:00'
FROM generate_series(CURRENT_DATE, CURRENT_DATE + INTERVAL '1 year', INTERVAL '1 day') AS g(work_date)
CROSS JOIN (VALUES (1), (3)) AS d(employee_id)
ON CONFLICT (employee_id, work_date, start_time) DO NOTHING;

COMMENT ON TABLE dentist_schedule IS '牙医排班；V12 以部署当日为起点铺一年诊所1排班，配合"当天起一个月"滚动可约窗口';
