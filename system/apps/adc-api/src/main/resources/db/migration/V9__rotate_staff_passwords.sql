-- 员工口令轮换（生产化第 4 步）
--
-- V7 里 6 个账号口令统一是演示值 adc12345，本迁移把它们换成强口令。
-- 新口令见 D:\ADC\docs\HANDOVER.md 的「15.1 员工账号」一节。
-- BCrypt cost=10，用项目自带的 spring-security-crypto 6.3.4 生成，验证 matches=true。
-- 所有账号仍共用一个口令（课程演示用）；上生产时建议一人一口令，届时再写一条 UPDATE 即可。

UPDATE user_account
   SET password_hash = '$2a$10$T.0bp/jZ2bHQrUdF75.5defDbT7/Wq0zzbC9H.YRUn4cbzgnCxK2q'
 WHERE username IN ('chenliqun', 'zhoumin', 'huangzhiyuan',
                    'linxiaoyu', 'wuqian', 'zhengwei');

-- 同时更新 V7 注释语义：演示口令已轮换，避免接手人误以为还是 adc12345。
COMMENT ON TABLE user_account IS '员工账号；口令已由 V9 轮换，见交接文档 15.1';
