-- github_history 테이블에 승인자와 작업내용 컬럼 추가

-- 승인자
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS approver VARCHAR(100);

-- 작업내용
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS work_content TEXT;

-- 주석 추가
COMMENT ON COLUMN github_history.approver IS '승인자';
COMMENT ON COLUMN github_history.work_content IS '작업내용';

