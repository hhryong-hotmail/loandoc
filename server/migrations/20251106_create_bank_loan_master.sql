-- 은행 대출 마스터 테이블 생성
CREATE TABLE bank_loan_master (
    bank_name VARCHAR(100) PRIMARY KEY,
    bank_code VARCHAR(20) NOT NULL,
    visa_types VARCHAR(10)[] NOT NULL,
    countries VARCHAR(50)[] NOT NULL,
    min_age INT NOT NULL,    
    --employment_date DATE,
    annual_income DECIMAL(15, 2) NOT NULL,
    stay_period INT NOT NULL,
    grade VARCHAR(10) NOT NULL,
    loan_limit DECIMAL(15, 2) NOT NULL,
    interest_rate DECIMAL(5, 2) NOT NULL,
    loan_months INT NOT NULL,
    max_limit DECIMAL(15, 2) NOT NULL,
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 컬럼 코멘트 추가
COMMENT ON COLUMN bank_loan_master.bank_name IS '은행명';
COMMENT ON COLUMN bank_loan_master.bank_code IS '은행코드';
COMMENT ON COLUMN bank_loan_master.visa_types IS '비자종류 배열 (E-9, E-7, F-2, F-4, H-2 등)';
COMMENT ON COLUMN bank_loan_master.countries IS '국가 배열 (Vietnam, Thailand, Philippines, Indonesia, Malaysia, Myanmar, Cambodia, Laos, Singapore, Timor-Leste 등)';
COMMENT ON COLUMN bank_loan_master.min_age IS '최소나이';
--COMMENT ON COLUMN bank_loan_master.employment_date IS '재직일자 (기준일자)';
COMMENT ON COLUMN bank_loan_master.annual_income IS '연소득 (단위: 만원)';
COMMENT ON COLUMN bank_loan_master.grade IS '등급 (A, B, C 등)';
COMMENT ON COLUMN bank_loan_master.loan_limit IS '한도 (단위: 만원)';
COMMENT ON COLUMN bank_loan_master.interest_rate IS '금리 (%)';
COMMENT ON COLUMN bank_loan_master.loan_months IS '대출개월수';
COMMENT ON COLUMN bank_loan_master.max_limit IS '최고한도 (단위: 만원)';
COMMENT ON COLUMN bank_loan_master.display_order IS '배열순서 (화면 표시 순서)';
COMMENT ON COLUMN bank_loan_master.created_at IS '생성일시';
COMMENT ON COLUMN bank_loan_master.updated_at IS '수정일시';

-- 인덱스 생성 (GIN 인덱스는 배열 검색에 효율적)
CREATE INDEX idx_bank_loan_visa_types ON bank_loan_master USING GIN(visa_types);
CREATE INDEX idx_bank_loan_countries ON bank_loan_master USING GIN(countries);
CREATE INDEX idx_bank_loan_grade ON bank_loan_master(grade);
CREATE INDEX idx_bank_loan_display_order ON bank_loan_master(display_order);

-- 테이블 코멘트
COMMENT ON TABLE bank_loan_master IS '은행 대출 상품 마스터 테이블 - 외국인 근로자 대출 조건 관리';

-- 샘플 데이터 삽입 (예시)
INSERT INTO bank_loan_master (
    bank_name, bank_code, visa_types, countries, min_age, visa_expiry_date,
    annual_income, stay_period, grade, loan_limit, 
    interest_rate, loan_months, max_limit, display_order
) VALUES 
    ('국민은행', 'KB', ARRAY['E-9', 'E-7'], ARRAY['Vietnam', 'Thailand'], 19, 2400, 12, 'A', 3000, 4.5, 36, 5000, 1),
    ('신한은행', 'SH', ARRAY['E-9'], ARRAY['Vietnam', 'Philippines'], 20, 2400, 12, 'B', 2500, 5.0, 36, 4000, 2),
    ('우리은행', 'WR', ARRAY['E-9', 'H-2'], ARRAY['Thailand', 'Myanmar'], 20, 2400, 12, 'A', 3000, 4.8, 36, 5000, 3),
    ('하나은행', 'HN', ARRAY['E-7', 'F-2'], ARRAY['Philippines', 'Indonesia'], 25, 3000, 6, 'A', 4000, 4.2, 48, 7000, 4),
    ('IBK기업은행', 'IBK', ARRAY['F-4', 'F-2'], ARRAY['Vietnam', 'Thailand', 'Philippines'], 22, 2000, 24, 'C', 2000, 6.0, 24, 3000, 5);

-- 데이터 수정
UPDATE bank_loan_master SET bank_name='KB저축은행' WHERE bank_name='국민은행';

-- 배열 데이터 조회 예시 쿼리
-- 1. 특정 비자 타입을 포함하는 대출 상품 찾기
-- SELECT * FROM bank_loan_master WHERE 'E-9' = ANY(visa_types);

-- 2. 특정 국가를 포함하는 대출 상품 찾기
-- SELECT * FROM bank_loan_master WHERE 'Vietnam' = ANY(countries);

-- 3. 여러 조건 동시 검색
-- SELECT * FROM bank_loan_master 
-- WHERE 'E-9' = ANY(visa_types) AND 'Vietnam' = ANY(countries);

-- 4. 배열 요소 개수 확인
-- SELECT bank_name, array_length(visa_types, 1) as visa_count 
-- FROM bank_loan_master;

-- 5. 배열을 행으로 펼치기 (unnest)
-- SELECT bank_name, unnest(visa_types) as visa_type 
-- FROM bank_loan_master;
