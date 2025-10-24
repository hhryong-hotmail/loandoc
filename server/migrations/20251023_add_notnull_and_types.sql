-- Migration: Make fields NOT NULL and set appropriate types for foreign_worker_master
-- Date: 2025-10-23
-- WARNING: Run in a transaction on a staging DB first. Backup production before applying.

BEGIN;

-- 1) Backup table
CREATE TABLE IF NOT EXISTS foreign_worker_master_backup AS TABLE foreign_worker_master WITH NO DATA;
INSERT INTO foreign_worker_master_backup SELECT * FROM foreign_worker_master;

-- 2) Data cleanup: convert empty strings in date columns to NULL, remove commas from numeric text
UPDATE foreign_worker_master
SET
  birth_date = NULLIF(TRIM(birth_date), ''),
  entry_date = NULLIF(TRIM(entry_date), ''),
  company_entry_date = NULLIF(TRIM(company_entry_date), ''),
  stay_expiry_date = NULLIF(TRIM(stay_expiry_date), ''),
  annual_salary = NULLIF(REGEXP_REPLACE(COALESCE(annual_salary::text, ''), '[,\s\u00A0]', '', 'g'), '')
WHERE TRUE;

-- 3) Cast/alter column types safely (use USING with explicit casts)
-- Adjust names if your schema differs. Test on staging.

-- annual_salary -> numeric
ALTER TABLE foreign_worker_master
  ALTER COLUMN annual_salary TYPE numeric USING (NULLIF(REGEXP_REPLACE(COALESCE(annual_salary::text, ''), '[,\s\u00A0]', '', 'g'), '')::numeric);

-- date columns -> date
ALTER TABLE foreign_worker_master
  ALTER COLUMN birth_date TYPE date USING (NULLIF(TRIM(birth_date), '')::date),
  ALTER COLUMN entry_date TYPE date USING (NULLIF(TRIM(entry_date), '')::date),
  ALTER COLUMN company_entry_date TYPE date USING (NULLIF(TRIM(company_entry_date), '')::date),
  ALTER COLUMN stay_expiry_date TYPE date USING (NULLIF(TRIM(stay_expiry_date), '')::date);

-- 4) Set NOT NULL constraints
ALTER TABLE foreign_worker_master
  ALTER COLUMN birth_date SET NOT NULL,
  ALTER COLUMN entry_date SET NOT NULL,
  ALTER COLUMN phone_number SET NOT NULL,
  ALTER COLUMN address SET NOT NULL,
  ALTER COLUMN home_country_address SET NOT NULL,
  ALTER COLUMN current_company SET NOT NULL,
  ALTER COLUMN company_entry_date SET NOT NULL,
  ALTER COLUMN annual_salary SET NOT NULL,
  ALTER COLUMN has_health_insurance SET NOT NULL,
  ALTER COLUMN stay_expiry_date SET NOT NULL,
  ALTER COLUMN has_loan SET NOT NULL;

COMMIT;

-- Rollback instructions (if something goes wrong):
-- ROLLBACK;
-- To restore from backup:
-- BEGIN; TRUNCATE foreign_worker_master; INSERT INTO foreign_worker_master SELECT * FROM foreign_worker_master_backup; COMMIT;
