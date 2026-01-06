-- github_history 테이블의 기존 데이터에 승인자 골고루 할당 (최종 버전)
-- 규칙:
-- - 개발에 관한 사항 → 하현용
-- - 테스트에 관한 사항 → 김종호
-- - 운영에 관한 사항 → 문규식
-- - 나머지는 ID 기반으로 순환 배정하여 골고루 할당

-- 먼저 모든 승인자 초기화
UPDATE github_history SET approver = NULL;

-- 1. stage_type이 '개발'인 경우 → 하현용
UPDATE github_history 
SET approver = '하현용'
WHERE stage_type = '개발';

-- 2. stage_type이 '테스트'인 경우 → 김종호
UPDATE github_history 
SET approver = '김종호'
WHERE stage_type = '테스트';

-- 3. stage_type이 '배포'인 경우 → 문규식
UPDATE github_history 
SET approver = '문규식'
WHERE stage_type = '배포';

-- 4. env_type이 '운영'인 경우 → 문규식
UPDATE github_history 
SET approver = '문규식'
WHERE approver IS NULL
  AND env_type = '운영';

-- 5. env_type이 '테스트'인 경우 → 김종호
UPDATE github_history 
SET approver = '김종호'
WHERE approver IS NULL
  AND env_type = '테스트';

-- 6. change_reason에 '운영', '배포', '프로덕션', 'production' 키워드가 있는 경우 → 문규식
UPDATE github_history 
SET approver = '문규식'
WHERE approver IS NULL
  AND (
    change_reason ILIKE '%운영%' 
    OR change_reason ILIKE '%배포%'
    OR change_reason ILIKE '%프로덕션%'
    OR change_reason ILIKE '%production%'
    OR change_reason ILIKE '%prod%'
  );

-- 7. change_reason에 '테스트', 'test' 키워드가 있는 경우 → 김종호
UPDATE github_history 
SET approver = '김종호'
WHERE approver IS NULL
  AND (
    change_reason ILIKE '%테스트%' 
    OR change_reason ILIKE '%test%'
  )
  AND NOT (
    change_reason ILIKE '%운영%' 
    OR change_reason ILIKE '%배포%'
    OR change_reason ILIKE '%프로덕션%'
    OR change_reason ILIKE '%production%'
  );

-- 8. program_name에 'test', 'Test', 'TEST'가 포함된 경우 → 김종호
UPDATE github_history 
SET approver = '김종호'
WHERE approver IS NULL
  AND (
    program_name ILIKE '%test%'
    OR program_name ILIKE '%loanTest%'
  )
  AND NOT (
    change_reason ILIKE '%운영%' 
    OR change_reason ILIKE '%배포%'
  );

-- 9. 나머지 데이터를 ID 기반으로 순환 배정하여 골고루 할당
-- ID를 3으로 나눈 나머지에 따라: 0→하현용, 1→김종호, 2→문규식
UPDATE github_history 
SET approver = CASE 
    WHEN id % 3 = 0 THEN '하현용'
    WHEN id % 3 = 1 THEN '김종호'
    WHEN id % 3 = 2 THEN '문규식'
END
WHERE approver IS NULL;

-- 결과 확인
SELECT 
    approver,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM github_history), 2) as percentage
FROM github_history
GROUP BY approver
ORDER BY approver;

