-- Add email column to foreign_worker_master table
-- 2025-11-11

-- Add email column (VARCHAR(255), allows NULL)
ALTER TABLE foreign_worker_master 
ADD COLUMN IF NOT EXISTS email VARCHAR(255);

-- Add comment to email column
COMMENT ON COLUMN foreign_worker_master.email IS 'Email address';
