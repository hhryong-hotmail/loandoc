-- bank_info 테이블을 bank_info_original로 복사 (구조와 데이터 모두)
-- 기존 테이블이 있으면 삭제
DROP TABLE IF EXISTS bank_info_original;

-- 테이블 구조와 데이터 복사
CREATE TABLE bank_info_original AS 
SELECT * FROM bank_info;

-- 인덱스와 제약조건도 복사하려면 아래 방법 사용 (주석 처리)
-- CREATE TABLE bank_info_original (LIKE bank_info INCLUDING ALL);
-- INSERT INTO bank_info_original SELECT * FROM bank_info;

-- 코멘트 복사
COMMENT ON TABLE bank_info_original IS '은행 정보 테이블 (원본 백업)';
COMMENT ON COLUMN bank_info_original.id IS 'id';
COMMENT ON COLUMN bank_info_original.bank_name IS '은행명';
COMMENT ON COLUMN bank_info_original.bank_code IS '은행코드';
COMMENT ON COLUMN bank_info_original.current_rate IS '대출금리';
COMMENT ON COLUMN bank_info_original.max_limit IS '최고한도';
COMMENT ON COLUMN bank_info_original.weight IS '가중치';

-- 복사 확인
SELECT COUNT(*) as total_rows FROM bank_info_original;

