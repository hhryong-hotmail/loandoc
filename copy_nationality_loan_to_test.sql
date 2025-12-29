-- nationality_loan 테이블을 test_nationality_loan으로 복사

-- 기존 테이블이 있으면 삭제
DROP TABLE IF EXISTS test_nationality_loan;

-- 테이블 구조 복사 (제약조건, 기본값, 주석 포함)
CREATE TABLE test_nationality_loan (LIKE nationality_loan INCLUDING ALL);

-- 데이터 복사
INSERT INTO test_nationality_loan 
SELECT * FROM nationality_loan;

-- 주석 복사
COMMENT ON TABLE test_nationality_loan IS '국적별 대출 상품 정보 테이블 (테스트용)';

COMMENT ON COLUMN test_nationality_loan.id IS 'ID (Primary Key)';
COMMENT ON COLUMN test_nationality_loan.bank_name IS '은행명';
COMMENT ON COLUMN test_nationality_loan.product_name IS '상품명';
COMMENT ON COLUMN test_nationality_loan.eligible_visa IS '대상비자 (예: E-7, E-9, F-2, F-6, F-5)';
COMMENT ON COLUMN test_nationality_loan.eligible_country IS '대상국가 (예: 네팔, 캄보디아, 고용허가제, 국내거주)';
COMMENT ON COLUMN test_nationality_loan.loan_limit_min IS '대출한도 최소값 (만원 단위)';
COMMENT ON COLUMN test_nationality_loan.loan_limit_max IS '대출한도 최대값 (만원 단위)';
COMMENT ON COLUMN test_nationality_loan.loan_period_min IS '대출기간 최소값 (개월 단위)';
COMMENT ON COLUMN test_nationality_loan.loan_period_max IS '대출기간 최대값 (개월 단위)';
COMMENT ON COLUMN test_nationality_loan.interest_rate_min IS '금리 최소값 (퍼센트)';
COMMENT ON COLUMN test_nationality_loan.interest_rate_max IS '금리 최대값 (퍼센트)';
COMMENT ON COLUMN test_nationality_loan.repayment_method IS '상환방식 (예: 원리금균등, 원금)';
COMMENT ON COLUMN test_nationality_loan.credit_rating IS '신용등급 (예: 475점 이상, 300점 이상, 은행심사기준)';
COMMENT ON COLUMN test_nationality_loan.age_min IS '나이 최소값';
COMMENT ON COLUMN test_nationality_loan.age_max IS '나이 최대값 (NULL인 경우 제한 없음)';
COMMENT ON COLUMN test_nationality_loan.remaining_stay_period_min IS '체류잔여기간 최소값 (개월 단위)';
COMMENT ON COLUMN test_nationality_loan.employment_period_min IS '재직기간 최소값 (개월 단위)';
COMMENT ON COLUMN test_nationality_loan.annual_income_min IS '연소득 최소값 (만원 단위)';
COMMENT ON COLUMN test_nationality_loan.health_insurance IS '의료보험 요구사항 (예: 지역가입자 불가)';
COMMENT ON COLUMN test_nationality_loan.required_documents IS '준비서류 목록';
COMMENT ON COLUMN test_nationality_loan.created_at IS '생성일시';
COMMENT ON COLUMN test_nationality_loan.updated_at IS '수정일시 (자동 업데이트)';

