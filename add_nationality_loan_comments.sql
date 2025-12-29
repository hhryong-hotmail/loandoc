-- nationality_loan 테이블의 모든 필드에 주석 추가

COMMENT ON TABLE nationality_loan IS '국적별 대출 상품 정보 테이블';

COMMENT ON COLUMN nationality_loan.id IS 'ID (Primary Key)';
COMMENT ON COLUMN nationality_loan.bank_name IS '은행명';
COMMENT ON COLUMN nationality_loan.product_name IS '상품명';
COMMENT ON COLUMN nationality_loan.eligible_visa IS '대상비자 (예: E-7, E-9, F-2, F-6, F-5)';
COMMENT ON COLUMN nationality_loan.eligible_country IS '대상국가 (예: 네팔, 캄보디아, 고용허가제, 국내거주)';
COMMENT ON COLUMN nationality_loan.loan_limit_min IS '대출한도 최소값 (만원 단위)';
COMMENT ON COLUMN nationality_loan.loan_limit_max IS '대출한도 최대값 (만원 단위)';
COMMENT ON COLUMN nationality_loan.loan_period_min IS '대출기간 최소값 (개월 단위)';
COMMENT ON COLUMN nationality_loan.loan_period_max IS '대출기간 최대값 (개월 단위)';
COMMENT ON COLUMN nationality_loan.interest_rate_min IS '금리 최소값 (퍼센트)';
COMMENT ON COLUMN nationality_loan.interest_rate_max IS '금리 최대값 (퍼센트)';
COMMENT ON COLUMN nationality_loan.repayment_method IS '상환방식 (예: 원리금균등, 원금)';
COMMENT ON COLUMN nationality_loan.credit_rating IS '신용등급 (예: 475점 이상, 300점 이상, 은행심사기준)';
COMMENT ON COLUMN nationality_loan.age_min IS '나이 최소값';
COMMENT ON COLUMN nationality_loan.age_max IS '나이 최대값 (NULL인 경우 제한 없음)';
COMMENT ON COLUMN nationality_loan.remaining_stay_period_min IS '체류잔여기간 최소값 (개월 단위)';
COMMENT ON COLUMN nationality_loan.employment_period_min IS '재직기간 최소값 (개월 단위)';
COMMENT ON COLUMN nationality_loan.annual_income_min IS '연소득 최소값 (만원 단위)';
COMMENT ON COLUMN nationality_loan.health_insurance IS '의료보험 요구사항 (예: 지역가입자 불가)';
COMMENT ON COLUMN nationality_loan.required_documents IS '준비서류 목록';
COMMENT ON COLUMN nationality_loan.created_at IS '생성일시';
COMMENT ON COLUMN nationality_loan.updated_at IS '수정일시 (자동 업데이트)';

