-- github_history 테이블에 결재번호 생성 및 환경/단계 값 할당

-- 1. 먼저 환경(env_type)과 단계(stage_type)를 임의로 할당
-- env_type: 테스트, 운영 중 랜덤 할당
-- stage_type: 개발, 테스트, 배포 중 랜덤 할당

-- ID 기반으로 순환 배정
UPDATE github_history 
SET env_type = CASE 
    WHEN id % 2 = 0 THEN '테스트'
    WHEN id % 2 = 1 THEN '운영'
END
WHERE env_type IS NULL OR env_type = '';

UPDATE github_history 
SET stage_type = CASE 
    WHEN id % 3 = 0 THEN '개발'
    WHEN id % 3 = 1 THEN '테스트'
    WHEN id % 3 = 2 THEN '배포'
END
WHERE stage_type IS NULL OR stage_type = '';

-- 2. 결재번호가 없는 데이터에 대해 날짜별로 결재번호 생성
-- 각 날짜별로 순차적으로 번호 부여

-- 먼저 임시 테이블을 사용하여 날짜별 순번 계산
WITH numbered_data AS (
    SELECT 
        id,
        change_datetime::date as change_date,
        ROW_NUMBER() OVER (PARTITION BY change_datetime::date ORDER BY id) as seq_num
    FROM github_history
    WHERE approval_number IS NULL OR approval_number = ''
)
UPDATE github_history h
SET approval_number = TO_CHAR(n.change_date, 'YYYYMMDD') || '-' || LPAD(n.seq_num::text, 3, '0')
FROM numbered_data n
WHERE h.id = n.id
  AND (h.approval_number IS NULL OR h.approval_number = '');

-- 3. 제출일자도 설정 (결재번호가 있는 경우)
UPDATE github_history
SET submitted_date = change_datetime
WHERE submitted_date IS NULL 
  AND approval_number IS NOT NULL 
  AND approval_number != '';

-- 결과 확인
SELECT 
    id,
    approval_number,
    env_type,
    stage_type,
    approver,
    change_datetime::date as change_date
FROM github_history
ORDER BY id
LIMIT 10;

-- 통계 확인
SELECT 
    env_type,
    stage_type,
    COUNT(*) as count
FROM github_history
GROUP BY env_type, stage_type
ORDER BY env_type, stage_type;

