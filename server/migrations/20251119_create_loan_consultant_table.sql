-- Migration: Create loan_consultant table (requested schema)
-- Date: 2025-11-19
-- Migration: Create loan_consultant table (requested schema)
-- Date: 2025-11-19

-- Drop any partially created objects from previous attempts
DROP INDEX IF EXISTS idx_loan_consultant_req_login;
DROP INDEX IF EXISTS idx_loan_consultant_req_type;
DROP INDEX IF EXISTS idx_loan_consultant_created_at;
DROP TABLE IF EXISTS loan_consultant;

CREATE TABLE IF NOT EXISTS loan_consultant (
    req_id SERIAL PRIMARY KEY,
    req_login VARCHAR(100) NOT NULL,
    counseler VARCHAR(100),
    name VARCHAR(100),
    phone_number VARCHAR(30),
    nationality VARCHAR(30),
    req_type VARCHAR(30),
    title VARCHAR(500) NOT NULL,
    req_content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_loan_consultant_req_login ON loan_consultant(req_login);
CREATE INDEX idx_loan_consultant_req_type ON loan_consultant(req_type);
CREATE INDEX idx_loan_consultant_created_at ON loan_consultant(created_at DESC);

-- Table and column comments (Korean)
COMMENT ON TABLE loan_consultant IS '상담요청 테이블 (loan_consultant)';
COMMENT ON COLUMN loan_consultant.req_id IS '요청 ID (PK)';
COMMENT ON COLUMN loan_consultant.req_login IS '요청자 로그인 아이디';
COMMENT ON COLUMN loan_consultant.counseler IS '담당 상담원';
COMMENT ON COLUMN loan_consultant.name IS '신청자 이름';
COMMENT ON COLUMN loan_consultant.phone_number IS '전화번호';
COMMENT ON COLUMN loan_consultant.nationality IS '국적';
COMMENT ON COLUMN loan_consultant.req_type IS '요청 유형 (질문, 답변, 완료, 미결 등)';
COMMENT ON COLUMN loan_consultant.title IS '요청 제목';
COMMENT ON COLUMN loan_consultant.req_content IS '요청 세부 내용';
COMMENT ON COLUMN loan_consultant.created_at IS '생성일시';
COMMENT ON COLUMN loan_consultant.updated_at IS '수정일시';
