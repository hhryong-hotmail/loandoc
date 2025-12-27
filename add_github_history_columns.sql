-- github_history 테이블에 필요한 필드 추가

-- 결재번호
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS approval_number VARCHAR(50);

-- 적용서버
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS target_server VARCHAR(100);

-- 테스트/운영 구분
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS env_type VARCHAR(20);

-- 개발/테스트/배포 구분
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS stage_type VARCHAR(20);

-- 적용날짜(테스트)
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS test_apply_date TIMESTAMP;

-- 적용날짜(운영)
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS prod_apply_date TIMESTAMP;

-- 제출일자
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS submitted_date TIMESTAMP;

-- 승인일자
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS approved_date TIMESTAMP;

-- 반려일자
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS rejected_date TIMESTAMP;

-- 반려이유
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- 운영서버 적용일자(예정)
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS prod_scheduled_date TIMESTAMP;

-- 주석 추가
COMMENT ON COLUMN github_history.approval_number IS '결재번호';
COMMENT ON COLUMN github_history.target_server IS '적용서버';
COMMENT ON COLUMN github_history.env_type IS '테스트/운영 구분';
COMMENT ON COLUMN github_history.stage_type IS '개발/테스트/배포 구분';
COMMENT ON COLUMN github_history.test_apply_date IS '적용날짜(테스트)';
COMMENT ON COLUMN github_history.prod_apply_date IS '적용날짜(운영)';
COMMENT ON COLUMN github_history.submitted_date IS '제출일자';
COMMENT ON COLUMN github_history.approved_date IS '승인일자';
COMMENT ON COLUMN github_history.rejected_date IS '반려일자';
COMMENT ON COLUMN github_history.rejection_reason IS '반려이유';
COMMENT ON COLUMN github_history.prod_scheduled_date IS '운영서버 적용일자(예정)';

