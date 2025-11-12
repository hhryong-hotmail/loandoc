-- Update passwords from dPtn1!1234 to dptn1!1234 (lowercase)
-- This SQL will re-hash all passwords as lowercase version

-- First, let's see current users (for backup)
SELECT user_id FROM user_account;

-- Update all user passwords to use lowercase version
-- Since we need to re-hash, we'll use a PostgreSQL function if bcrypt is available
-- Or we can do this via application code

-- For now, this is a placeholder - actual update will be done via terminal
