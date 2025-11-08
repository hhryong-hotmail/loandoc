-- Migration: Add comments to columns of documents table
-- Date: 2025-11-01

/*
 This migration adds human-readable comments to columns in the `documents` table.
 Each COMMENT ON COLUMN is executed only if the column exists to keep the migration idempotent
 and safe to run on databases where some columns may be absent.
*/

-- We avoid nested dollar-quoting by using format(...)
BEGIN;

DO $$
DECLARE
  _tbl text := 'documents';
  _col text;
  _comment text;
BEGIN
  -- helper to conditionally set comment using format() to avoid nested dollar quoting
  FOR _col, _comment IN VALUES
    ('id', 'PK, 문서 고유 ID (serial) / Primary key for documents table.'),
    ('doc_key', '문서 식별 키 (doc_key) - 동의서/약관 등의 논리적 키. 예: "개인정보 수집 이용 동의"'),
    ('version', '문서 버전 문자열 (version) - 문서의 버전 또는 릴리스 태그 (예: v2025-10-27).'),
    ('title', '문서 제목 (title) - 사용자에게 표시되는 문서의 짧은 제목.'),
    ('content', '문서 본문(content) - 마크다운/HTML 또는 텍스트 형식의 전체 문서 내용.'),
    ('effective_at', '시행일(effective_at) - 문서의 적용 시작일시 (타임스탬프 with time zone 권장).'),
    ('created_by', '작성자(created_by) - 문서를 등록한 사용자 또는 시스템 식별자.'),
    ('notes', '관리용 메모(notes) - 내부 운영 메모나 관리자 설명을 저장.'),
    ('group_name', '문서 그룹명(group_name) - 문서를 논리적 그룹으로 묶기 위한 이름(예: 대출동의, 개인정보동의).'),
    ('group_number', '문서 그룹 번호(group_number) - 정렬/우선순위 또는 외부 매핑용 숫자 값.'),
    ('select_option', '선택 옵션(select_option) - 필수/선택 등 사용자 동의 옵션을 나타내는 값(예: 필수, 선택).'),
    ('is_active', '활성 여부(is_active) - 현재 사용 가능한(표시되는) 문서인지 여부 (boolean).'),
    ('created_at', '생성 시각(created_at) - 문서 레코드가 생성된 타임스탬프.'),
    ('updated_at', '수정 시각(updated_at) - 마지막으로 수정된 타임스탬프.')
  LOOP
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = _tbl AND column_name = _col) THEN
      EXECUTE format('COMMENT ON COLUMN %I.%I IS %L', _tbl, _col, _comment);
    END IF;
  END LOOP;
END$$;

COMMIT;

-- Notes:
-- - This migration is safe to run multiple times. If a column is missing, its COMMENT is skipped.
-- - Adjust or extend the list above if your `documents` table contains additional columns you want commented.
