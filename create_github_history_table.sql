-- GitHub 히스토리 테이블 생성
-- 형상관리시스템을 위한 기본 테이블

CREATE TABLE IF NOT EXISTS github_history (
    id SERIAL PRIMARY KEY,
    database_name VARCHAR(100),
    repo_name VARCHAR(200),
    change_datetime TIMESTAMP,
    program_name TEXT,
    change_reason TEXT,
    developer_name VARCHAR(100),
    important_code_content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 결재 관련 컬럼 추가 (add_github_history_columns.sql에서 이미 추가되었을 수 있음)
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS approval_number VARCHAR(50);
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS target_server VARCHAR(100);
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS env_type VARCHAR(20);
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS stage_type VARCHAR(20);
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS test_apply_date TIMESTAMP;
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS prod_apply_date TIMESTAMP;
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS submitted_date TIMESTAMP;
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS approved_date TIMESTAMP;
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS rejected_date TIMESTAMP;
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE github_history ADD COLUMN IF NOT EXISTS prod_scheduled_date TIMESTAMP;

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_github_history_change_datetime ON github_history(change_datetime);
CREATE INDEX IF NOT EXISTS idx_github_history_developer_name ON github_history(developer_name);
CREATE INDEX IF NOT EXISTS idx_github_history_submitted_date ON github_history(submitted_date);
CREATE INDEX IF NOT EXISTS idx_github_history_approved_date ON github_history(approved_date);
CREATE INDEX IF NOT EXISTS idx_github_history_rejected_date ON github_history(rejected_date);
CREATE INDEX IF NOT EXISTS idx_github_history_env_type ON github_history(env_type);
CREATE INDEX IF NOT EXISTS idx_github_history_stage_type ON github_history(stage_type);

-- 주석 추가
COMMENT ON TABLE github_history IS 'GitHub 변경이력 및 형상관리 테이블';
COMMENT ON COLUMN github_history.id IS '히스토리 ID';
COMMENT ON COLUMN github_history.database_name IS '데이터베이스명';
COMMENT ON COLUMN github_history.repo_name IS '저장소명';
COMMENT ON COLUMN github_history.change_datetime IS '변경일시';
COMMENT ON COLUMN github_history.program_name IS '프로그램명 (변경된 파일 목록)';
COMMENT ON COLUMN github_history.change_reason IS '변경사유';
COMMENT ON COLUMN github_history.developer_name IS '개발자명';
COMMENT ON COLUMN github_history.important_code_content IS '중요 코드 내용';
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

-- updated_at 자동 업데이트를 위한 트리거 함수
CREATE OR REPLACE FUNCTION update_github_history_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 트리거 생성
DROP TRIGGER IF EXISTS trigger_update_github_history_updated_at ON github_history;
CREATE TRIGGER trigger_update_github_history_updated_at
    BEFORE UPDATE ON github_history
    FOR EACH ROW
    EXECUTE FUNCTION update_github_history_updated_at();

