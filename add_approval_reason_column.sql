-- github_history 테이블에 승인사유 및 조건 컬럼 추가

-- 승인사유 및 조건
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS approval_reason TEXT;

-- 주석 추가
COMMENT ON COLUMN github_history.approval_reason IS '승인사유 및 조건';

