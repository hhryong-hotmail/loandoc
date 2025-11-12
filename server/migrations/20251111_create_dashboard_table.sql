-- Migration: Create dashboard table
-- Date: 2025-11-11

CREATE TABLE IF NOT EXISTS dashboard (
    msg_id SERIAL PRIMARY KEY,
    author VARCHAR(100) NOT NULL,
    password VARCHAR(100),
    title VARCHAR(500) NOT NULL,
    msg_type VARCHAR(50) DEFAULT 'general',
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add index for faster search
CREATE INDEX IF NOT EXISTS idx_dashboard_msg_type ON dashboard(msg_type);
CREATE INDEX IF NOT EXISTS idx_dashboard_author ON dashboard(author);
CREATE INDEX IF NOT EXISTS idx_dashboard_created_at ON dashboard(created_at DESC);
