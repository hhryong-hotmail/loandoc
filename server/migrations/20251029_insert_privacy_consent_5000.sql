-- Migration: Insert consent document (개인정보 수집 이용 동의 -5000)
-- Date: 2025-10-29
-- Idempotent: uses ON CONFLICT to update existing rows and a single CTE chain to (de)activate atomically.

BEGIN;

WITH ins AS (
  INSERT INTO documents (
    doc_key, version, title, content, effective_at, created_by, notes, group_name, group_number, select_option
  ) VALUES (
    '개인정보 수집 이용 동의 -5000',
    'v2025-10-27',
    '개인정보 수집 이용 동의 (2025.10.29)',
    $DOC_5000$
개인정보 수집 이용 동의 (2025.10.29)

MKF파트너스 주식회사(이하 “회사”)는 「신용정보의 이용 및 보호에 관한 법률」, 「정보통신망 이용촉진 및 정보보호 등에 관한 법률」, 「개인정보 보호법」 등 관련 법규에 따라 이용자로부터 아래와 같은 개인정보 수집・이용 동의를 받고자 합니다. 내용을 자세히 읽으신 후, 동의하시면 원래 화면으로 돌아가 “동의”, “계속”, “인증번호 요청” 등 해당 화면의 안내에 따라 버튼을 눌러 주시기 바랍니다. 보다 자세한 내용은 회사의 개인정보처리방침을 참조하여 주시기제1조 (처리 목적)

회사는 다음의 목적으로 개인(신용)정보를 처리하고 있습니다.

1. 회원 가입 및 관리

회원가입의사 확인, 회원제 서비스 제공에 따른 본인 식별ㆍ인증, 회원관리, 회원 가입 경로파악, 서비스 부정이용 방지, 각 종 고지ㆍ통지, 고충처리 등을 목적으로 개인정보를 처리합니다.

... (문서 본문 생략 가능; 실제 파일에는 전체 내용 그대로 넣으세요) ...

제3조 (시행일)

이 < 개인정보 수집·이용 동의>은 2024. 11. 04. 부터 시행됩니다.

공고일자 : 2024. 11. 04

시행일자 : 2024. 11. 04

$DOC_5000$,
    '2025-10-27T00:00:00+09',
    'admin',
    '관리자 등록',
    '대출비교서비스',
    5000,
    '필수'
  )
  -- If a row with same (doc_key, version) exists, update selected fields (idempotent)
  ON CONFLICT (doc_key, version) DO UPDATE
  SET
    title = EXCLUDED.title,
    content = CASE WHEN COALESCE(EXCLUDED.content, '') = '' THEN documents.content ELSE EXCLUDED.content END,
    effective_at = EXCLUDED.effective_at,
    created_by = EXCLUDED.created_by,
    notes = EXCLUDED.notes,
    group_name = EXCLUDED.group_name,
    group_number = EXCLUDED.group_number,
    select_option = EXCLUDED.select_option,
    updated_at = now()
  RETURNING id
), deactivated AS (
  UPDATE documents
  SET is_active = false
  WHERE doc_key = '개인정보 수집 이용 동의 -5000'
    AND id NOT IN (SELECT id FROM ins)
  RETURNING id
), activated AS (
  UPDATE documents
  SET is_active = true
  WHERE id IN (SELECT id FROM ins)
  RETURNING id
)
-- Return the inserted/updated id so a migration runner can assert success
SELECT id AS new_id FROM ins;

COMMIT;
