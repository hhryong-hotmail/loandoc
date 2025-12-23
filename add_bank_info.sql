-- bank_info 테이블 생성 및 데이터 입력 (가중치, 최고한도 포함)
CREATE TABLE bank_info (
    id SERIAL PRIMARY KEY,
    bank_name VARCHAR(100) NOT NULL,
    bank_code VARCHAR(10) NOT NULL,
    current_rate DECIMAL(5,2) NOT NULL,
    max_limit BIGINT NOT NULL,
    weight DECIMAL(5,2) NOT NULL
);

INSERT INTO bank_info (bank_name, bank_code, current_rate, max_limit, weight) VALUES
('전북은행', '037', 13.07, 50000000, 36.00),
('KB저축은행', '050', 14.70, 30000000, 35.00),
('OK저축은행', '050', 15.00, 35000000, 37.00),
('웰컴저축은행', '050', 16.00, 30000000, 35.00),
('예가람저축은행', '050', 16.50, 40000000, 38.00);

-- 필드별 설명(remarks) 추가 (PostgreSQL용)
COMMENT ON TABLE bank_info IS '은행 정보 테이블';
COMMENT ON COLUMN bank_info.id IS 'id';
COMMENT ON COLUMN bank_info.bank_name IS '은행명';
COMMENT ON COLUMN bank_info.bank_code IS '은행코드';
COMMENT ON COLUMN bank_info.current_rate IS '대출금리';
COMMENT ON COLUMN bank_info.max_limit IS '최고한도';
COMMENT ON COLUMN bank_info.weight IS '가중치';
