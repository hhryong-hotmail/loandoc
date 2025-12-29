-- nationalityLoan 테이블 생성
CREATE TABLE IF NOT EXISTS nationality_loan (
    id SERIAL PRIMARY KEY,
    bank_name VARCHAR(100) NOT NULL,
    product_name VARCHAR(200),
    eligible_visa TEXT,
    eligible_country TEXT,
    loan_limit_min BIGINT,
    loan_limit_max BIGINT,
    loan_period_min INTEGER,
    loan_period_max INTEGER,
    interest_rate_min DECIMAL(5,2),
    interest_rate_max DECIMAL(5,2),
    repayment_method VARCHAR(100),
    credit_rating VARCHAR(100),
    age_min INTEGER,
    age_max INTEGER,
    remaining_stay_period_min INTEGER,
    employment_period_min INTEGER,
    annual_income_min BIGINT,
    health_insurance TEXT,
    required_documents TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 주석 추가
COMMENT ON TABLE nationality_loan IS '국적별 대출 상품 정보 테이블';
COMMENT ON COLUMN nationality_loan.id IS 'ID';
COMMENT ON COLUMN nationality_loan.bank_name IS '은행명';
COMMENT ON COLUMN nationality_loan.product_name IS '상품명';
COMMENT ON COLUMN nationality_loan.eligible_visa IS '대상비자';
COMMENT ON COLUMN nationality_loan.eligible_country IS '대상국가';
COMMENT ON COLUMN nationality_loan.loan_limit_min IS '대출한도 최소값 (만원)';
COMMENT ON COLUMN nationality_loan.loan_limit_max IS '대출한도 최대값 (만원)';
COMMENT ON COLUMN nationality_loan.loan_period_min IS '대출기간 최소값 (개월)';
COMMENT ON COLUMN nationality_loan.loan_period_max IS '대출기간 최대값 (개월)';
COMMENT ON COLUMN nationality_loan.interest_rate_min IS '금리 최소값 (%)';
COMMENT ON COLUMN nationality_loan.interest_rate_max IS '금리 최대값 (%)';
COMMENT ON COLUMN nationality_loan.repayment_method IS '상환방식';
COMMENT ON COLUMN nationality_loan.credit_rating IS '신용등급';
COMMENT ON COLUMN nationality_loan.age_min IS '나이 최소값';
COMMENT ON COLUMN nationality_loan.age_max IS '나이 최대값';
COMMENT ON COLUMN nationality_loan.remaining_stay_period_min IS '체류잔여기간 최소값 (개월)';
COMMENT ON COLUMN nationality_loan.employment_period_min IS '재직기간 최소값 (개월)';
COMMENT ON COLUMN nationality_loan.annual_income_min IS '연소득 최소값 (만원)';
COMMENT ON COLUMN nationality_loan.health_insurance IS '의료보험';
COMMENT ON COLUMN nationality_loan.required_documents IS '준비서류';

-- updated_at 자동 업데이트를 위한 트리거 함수
CREATE OR REPLACE FUNCTION update_nationality_loan_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 트리거 생성
DROP TRIGGER IF EXISTS trigger_update_nationality_loan_updated_at ON nationality_loan;
CREATE TRIGGER trigger_update_nationality_loan_updated_at
    BEFORE UPDATE ON nationality_loan
    FOR EACH ROW
    EXECUTE FUNCTION update_nationality_loan_updated_at();

-- 데이터 삽입
INSERT INTO nationality_loan (
    bank_name, product_name, eligible_visa, eligible_country,
    loan_limit_min, loan_limit_max, loan_period_min, loan_period_max,
    interest_rate_min, interest_rate_max, repayment_method, credit_rating,
    age_min, age_max, remaining_stay_period_min, employment_period_min,
    annual_income_min, health_insurance, required_documents
) VALUES
-- 1. KB저축은행 - kiwi Dream Loan
('KB저축은행', 'kiwi Dream Loan', 'E-7, E-9, F-2, F-6, F-5', '네팔, 캄보디아',
 100, 3000, 5, 36,
 12.43, 19.9, '원리금균등', '475점 이상',
 19, NULL, 8, 3,
 1500, '지역가입자 불가', '외국인등록증, 건강보험자격득실확인서, 보험료납부확인서, 급여통장거래내역서'),

-- 2. 전북은행 - JB Bravo Korea 대출
('전북은행', 'JB Bravo Korea 대출', 'E-7, E-9, F-2, F-6, F-5, F-4', '고용허가제',
 100, 5000, 3, 48,
 9.7, 17.9, '원금, 원리금균등', '은행심사기준',
 19, NULL, 3, 1,
 1500, NULL, '외국인등록증, 표준근로계약서, 재직증명서, 소득확인서류'),

-- 3. OK저축은행 - Hi-OK론
('OK저축은행', 'Hi-OK론', 'E-9', '고용허가제',
 100, 3500, 1, 36,
 13.69, 19.99, '원리금균등', '300점 이상',
 18, 45, NULL, NULL,
 NULL, NULL, '외국인등록증, 여권, 표준근로계약서, 소득확인서류'),

-- 4. 웰컴저축은행 - 웰컴외국인대출
('웰컴저축은행', '웰컴외국인대출', 'E-9, E-7', '국내거주',
 100, 3000, 6, 36,
 15.53, 18.93, '원리금균등', '300점 이상',
 NULL, NULL, 1, NULL,
 NULL, NULL, '외국인등록증, 여권, 재직확인서류, 소득확인서류'),

-- 5. 예가람저축은행 - Oh! YES loan
('예가람저축은행', 'Oh! YES loan', 'E-7, E-9, F-2, F-6, F-5', '고용허가제',
 500, 4000, 6, 33,
 15.5, 18.9, '원리금균등', '300점 이상',
 20, NULL, NULL, NULL,
 NULL, NULL, '외국인등록증, 여권, 재직확인서류, 소득확인서류');

