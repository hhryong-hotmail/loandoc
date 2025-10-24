-- Create dashboard table if it doesn't exist
CREATE TABLE IF NOT EXISTS dashboard (
    id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    author VARCHAR(100) DEFAULT 'Anonymous',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster queries
CREATE INDEX IF NOT EXISTS idx_dashboard_created_at ON dashboard(created_at DESC);

-- Insert sample data for testing (only if table is empty)
INSERT INTO dashboard (title, content, author)
SELECT '환영합니다!', '외국인 근로자 대출 중개 포털 게시판입니다. 궁금한 사항이 있으시면 자유롭게 글을 작성해주세요.', 'LOANDOC 관리자'
WHERE NOT EXISTS (SELECT 1 FROM dashboard LIMIT 1);

-- Display table structure
\d dashboard;

-- Show current records
SELECT id, title, author, created_at FROM dashboard ORDER BY created_at DESC;
