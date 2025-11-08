-- Migration: 20251027_add_comments_to_documents_table.sql
-- Purpose: `documents` 테이블 및 컬럼에 설명(comment) 추가
-- Apply with psql or your migration tooling.

-- This migration adds comments to the `documents` table and its columns.
-- It is written defensively: each COMMENT is executed only if the target table/column exists,
-- and potential errors are caught so a missing object doesn't abort the whole script.

-- Table comment (only if table exists)
DO $$
BEGIN
	IF EXISTS (
		SELECT 1 FROM information_schema.tables
		WHERE table_schema = current_schema() AND table_name = 'documents'
	) THEN
		EXECUTE $$COMMENT ON TABLE documents IS '서비스 문서(예: 이용약관, 개인정보처리방침 등) 관리 테이블';$$;
	ELSE
		RAISE NOTICE 'Skipping table comment: table documents does not exist in schema %', current_schema();
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error adding table comment: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

-- Column comments (each guarded by existence check)
DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'id') THEN
		EXECUTE $$COMMENT ON COLUMN documents.id IS 'PK. 문서 레코드 식별자 (BIGINT, IDENTITY 자동증가)';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.id (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.id: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'doc_key') THEN
		EXECUTE $$COMMENT ON COLUMN documents.doc_key IS '문서 식별자 (논리적 키). 예: terms_of_service, privacy_policy';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.doc_key (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.doc_key: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'version') THEN
		EXECUTE $$COMMENT ON COLUMN documents.version IS '문서 버전 레이블 (예: v1.0, 2025-10-27)';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.version (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.version: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'title') THEN
		EXECUTE $$COMMENT ON COLUMN documents.title IS '문서 제목';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.title (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.title: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'content') THEN
		EXECUTE $$COMMENT ON COLUMN documents.content IS '문서 본문(HTML 또는 Markdown 텍스트)';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.content (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.content: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'effective_at') THEN
		EXECUTE $$COMMENT ON COLUMN documents.effective_at IS '해당 문서 버전의 시행(발효) 시각 (TIMESTAMPTZ)';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.effective_at (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.effective_at: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'is_active') THEN
		EXECUTE $$COMMENT ON COLUMN documents.is_active IS '현재 활성(사이트에 표시되는 기본 버전) 여부 (BOOLEAN)';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.is_active (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.is_active: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'created_by') THEN
		EXECUTE $$COMMENT ON COLUMN documents.created_by IS '작성자 식별자(관리자 ID 또는 작성자명)';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.created_by (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.created_by: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'created_at') THEN
		EXECUTE $$COMMENT ON COLUMN documents.created_at IS '레코드 생성 시각 (TIMESTAMPTZ)';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.created_at (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.created_at: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'updated_at') THEN
		EXECUTE $$COMMENT ON COLUMN documents.updated_at IS '레코드 최종 수정 시각 (TIMESTAMPTZ)';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.updated_at (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.updated_at: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'documents' AND column_name = 'notes') THEN
		EXECUTE $$COMMENT ON COLUMN documents.notes IS '관리자 메모(변경 사유 등)';$$;
	ELSE
		RAISE NOTICE 'Skipping comment for documents.notes (column missing)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	RAISE NOTICE 'Error commenting documents.notes: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;
