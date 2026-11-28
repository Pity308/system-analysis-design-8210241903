-- 补齐诊所1的排班：从 2026-11-28（当天）到 2026-12-28（下个月），每天都有班可约。
--
-- 背景：V2 种子的排班是按开发时的相对日期写死的（27/28/30/12-01/12-02），
-- 且 11/28 的排班落在诊所4、不在默认的诊所1；29号所有诊所都没排班。
-- 用户要求"当天起包括当天、到下个月都能预约"，故补这段连续排班。
--
-- 牙医1（主任医师，资质1、2）与牙医3（主治医师，3 项资质）在诊所1每天 09:00–18:00。
-- 用 ON CONFLICT DO NOTHING 保证可重复执行、不与已有排班冲突（唯一键 employee_id+work_date+start_time）。
-- 注意：不修改任何已应用迁移，只新增 V11。

INSERT INTO dentist_schedule (employee_id, clinic_id, work_date, start_time, end_time)
SELECT d.employee_id, 1, g.work_date, TIME '09:00:00', TIME '18:00:00'
FROM generate_series(DATE '2026-11-28', DATE '2026-12-28', INTERVAL '1 day') AS g(work_date)
CROSS JOIN (VALUES (1), (3)) AS d(employee_id)
ON CONFLICT (employee_id, work_date, start_time) DO NOTHING;

COMMENT ON TABLE dentist_schedule IS '牙医排班；V11 补齐诊所1从 2026-11-28 至 2026-12-28 每日排班，保证每天可约';
