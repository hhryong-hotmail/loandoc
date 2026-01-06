-- github_history 테이블의 기존 데이터에 승인자 할당
-- 규칙:
-- - 개발에 관한 사항 → 하현용
-- - 테스트에 관한 사항 → 김종호
-- - 운영에 관한 사항 → 문규식

-- 1. stage_type이 '개발'인 경우 → 하현용
UPDATE github_history 
SET approver = '하현용'
WHERE (approver IS NULL OR approver = '')
  AND stage_type = '개발';

-- 2. stage_type이 '테스트'인 경우 → 김종호
UPDATE github_history 
SET approver = '김종호'
WHERE (approver IS NULL OR approver = '')
  AND stage_type = '테스트';

-- 3. stage_type이 '배포'인 경우 → 문규식
UPDATE github_history 
SET approver = '문규식'
WHERE (approver IS NULL OR approver = '')
  AND stage_type = '배포';

-- 4. env_type이 '운영'인 경우 → 문규식
UPDATE github_history 
SET approver = '문규식'
WHERE (approver IS NULL OR approver = '')
  AND env_type = '운영';

-- 5. env_type이 '테스트'인 경우 → 김종호
UPDATE github_history 
SET approver = '김종호'
WHERE (approver IS NULL OR approver = '')
  AND env_type = '테스트';

-- 6. change_reason에 '운영', '배포', '프로덕션', 'production' 키워드가 있는 경우 → 문규식
UPDATE github_history 
SET approver = '문규식'
WHERE (approver IS NULL OR approver = '')
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
WHERE (approver IS NULL OR approver = '')
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
WHERE (approver IS NULL OR approver = '')
  AND (
    program_name ILIKE '%test%'
    OR program_name ILIKE '%loanTest%'
  )
  AND NOT (
    change_reason ILIKE '%운영%' 
    OR change_reason ILIKE '%배포%'
  );

-- 9. 나머지 모든 경우 (개발 관련) → 하현용
UPDATE github_history 
SET approver = '하현용'
WHERE approver IS NULL OR approver = '';

-- 결과 확인
SELECT 
    approver,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM github_history), 2) as percentage
FROM github_history
GROUP BY approver
ORDER BY approver;

