-- 운영 매뉴얼 및 개발 매뉴얼 관리 테이블 생성

-- 매뉴얼 관리 테이블
CREATE TABLE IF NOT EXISTS manual_management (
    id SERIAL PRIMARY KEY,
    manual_type VARCHAR(20) NOT NULL CHECK (manual_type IN ('운영', '개발')),
    version VARCHAR(50) NOT NULL,
    manual_date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    content TEXT,
    file_path VARCHAR(1000),
    file_name VARCHAR(500),
    file_size BIGINT,
    file_type VARCHAR(50),
    status VARCHAR(20) DEFAULT '작성중' CHECK (status IN ('작성중', '완료', '삭제', '보류')),
    writer VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    remarks TEXT,
    UNIQUE(manual_type, version, manual_date)
);

-- 매뉴얼 변경 이력 테이블
CREATE TABLE IF NOT EXISTS manual_history (
    id SERIAL PRIMARY KEY,
    manual_id INTEGER NOT NULL REFERENCES manual_management(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL,
    change_type VARCHAR(20) NOT NULL CHECK (change_type IN ('생성', '수정', '삭제', '복원', '버전업')),
    change_reason TEXT,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    old_content TEXT,
    new_content TEXT,
    old_file_path VARCHAR(1000),
    new_file_path VARCHAR(1000)
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_manual_type_date ON manual_management(manual_type, manual_date);
CREATE INDEX IF NOT EXISTS idx_manual_version ON manual_management(version);
CREATE INDEX IF NOT EXISTS idx_manual_status ON manual_management(status);
CREATE INDEX IF NOT EXISTS idx_manual_history_manual_id ON manual_history(manual_id);
CREATE INDEX IF NOT EXISTS idx_manual_history_changed_at ON manual_history(changed_at);

-- 주석 추가
COMMENT ON TABLE manual_management IS '운영/개발 매뉴얼 관리 테이블';
COMMENT ON COLUMN manual_management.id IS '매뉴얼 ID';
COMMENT ON COLUMN manual_management.manual_type IS '매뉴얼 타입 (운영/개발)';
COMMENT ON COLUMN manual_management.version IS '버전';
COMMENT ON COLUMN manual_management.manual_date IS '매뉴얼 일자';
COMMENT ON COLUMN manual_management.title IS '매뉴얼 제목';
COMMENT ON COLUMN manual_management.content IS '매뉴얼 내용';
COMMENT ON COLUMN manual_management.file_path IS '파일 경로';
COMMENT ON COLUMN manual_management.file_name IS '파일명';
COMMENT ON COLUMN manual_management.file_size IS '파일 크기 (bytes)';
COMMENT ON COLUMN manual_management.file_type IS '파일 타입 (pdf, docx, html 등)';
COMMENT ON COLUMN manual_management.status IS '상태 (작성중/완료/삭제/보류)';
COMMENT ON COLUMN manual_management.writer IS '작성자';
COMMENT ON COLUMN manual_management.created_at IS '생성일시';
COMMENT ON COLUMN manual_management.updated_at IS '수정일시';
COMMENT ON COLUMN manual_management.deleted_at IS '삭제일시';
COMMENT ON COLUMN manual_management.remarks IS '비고';

COMMENT ON TABLE manual_history IS '매뉴얼 변경 이력 테이블';
COMMENT ON COLUMN manual_history.id IS '이력 ID';
COMMENT ON COLUMN manual_history.manual_id IS '매뉴얼 ID (FK)';
COMMENT ON COLUMN manual_history.version IS '버전';
COMMENT ON COLUMN manual_history.change_type IS '변경 타입 (생성/수정/삭제/복원/버전업)';
COMMENT ON COLUMN manual_history.change_reason IS '변경 사유';
COMMENT ON COLUMN manual_history.changed_by IS '변경자';
COMMENT ON COLUMN manual_history.changed_at IS '변경일시';
COMMENT ON COLUMN manual_history.old_content IS '이전 내용';
COMMENT ON COLUMN manual_history.new_content IS '새 내용';
COMMENT ON COLUMN manual_history.old_file_path IS '이전 파일 경로';
COMMENT ON COLUMN manual_history.new_file_path IS '새 파일 경로';

-- updated_at 자동 업데이트를 위한 트리거 함수
CREATE OR REPLACE FUNCTION update_manual_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 트리거 생성
DROP TRIGGER IF EXISTS trigger_update_manual_updated_at ON manual_management;
CREATE TRIGGER trigger_update_manual_updated_at
    BEFORE UPDATE ON manual_management
    FOR EACH ROW
    EXECUTE FUNCTION update_manual_updated_at();

