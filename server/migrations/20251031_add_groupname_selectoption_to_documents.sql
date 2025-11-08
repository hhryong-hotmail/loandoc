-- Migration: Add group_name and select_option columns to documents (idempotent)
-- Date: 2025-10-31

BEGIN;

-- Add columns only if they do not already exist. This migration is safe to run multiple times.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'documents' AND column_name = 'group_name'
  ) THEN
    ALTER TABLE public.documents ADD COLUMN group_name TEXT;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'documents' AND column_name = 'select_option'
  ) THEN
    ALTER TABLE public.documents ADD COLUMN select_option TEXT;
  END IF;
END$$;

COMMIT;

-- Notes:
-- - The migration uses a DO block to check information_schema and ALTER the table only when needed.
-- - New columns are nullable by default to avoid breaking existing INSERTs. If you need NOT NULL and defaults,
--   add a follow-up migration that sets a default and backfills values safely.
