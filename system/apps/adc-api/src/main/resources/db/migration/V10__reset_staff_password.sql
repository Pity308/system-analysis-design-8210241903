-- 员工口令改为 123456（2026-11-28，课程演示用）
--
-- V9 把演示口令轮换为强口令，本迁移再按用户要求改为 123456，方便老师在课堂上操作。
-- 注意：123456 是弱口令，仅限课程演示；正式上线必须再换回强口令（新增 V11，不要改本文件）。
-- BCrypt cost=10，用项目自带 spring-security-crypto 6.3.4 生成，matches=true 已验证。

UPDATE user_account
   SET password_hash = '$2a$10$7SbRipR7ii9XlIJj8OeGMurrsOR6YqIIhVlVfS27Z/P3xGwoCyU0a'
 WHERE username IN ('chenliqun', 'zhoumin', 'huangzhiyuan',
                    'linxiaoyu', 'wuqian', 'zhengwei');

COMMENT ON TABLE user_account IS '员工账号；口令为课程演示用 123456（V10），正式上线须再轮换';
