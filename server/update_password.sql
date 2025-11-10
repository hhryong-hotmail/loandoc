-- 모든 사용자의 비밀번호를 dPtn1!1234로 변경
-- bcrypt 해시: $2a$12$0ekRn2eW1t8OtjswIOICfeCKTyuCCL1M7LqVOUw6ooEoYCTMD4hvW

-- 업데이트 전 총 사용자 수 확인
SELECT COUNT(*) as total_users FROM user_account;

-- 모든 사용자의 비밀번호 업데이트
UPDATE user_account 
SET password = '$2a$12$0ekRn2eW1t8OtjswIOICfeCKTyuCCL1M7LqVOUw6ooEoYCTMD4hvW';

-- 업데이트된 행 수 확인
SELECT COUNT(*) as updated_users, 
       substring(password, 1, 20) as common_password_hash 
FROM user_account 
GROUP BY password;

-- 샘플 사용자 확인 (처음 10명)
SELECT user_id, substring(password, 1, 30) as password_hash_preview 
FROM user_account 
ORDER BY user_id 
LIMIT 10;
