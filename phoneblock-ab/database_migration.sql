-- Database migration script for Call Retention Policy feature (Simplified)
-- Add single retention period column to the ANSWERBOT_SIP table

-- Add retention period setting (NEVER is the default for backwards compatibility)
ALTER TABLE ANSWERBOT_SIP 
ADD COLUMN RETENTION_PERIOD VARCHAR(10) DEFAULT 'NEVER';

-- Create index for efficient retention cleanup queries
CREATE INDEX idx_answerbot_retention 
ON ANSWERBOT_SIP (RETENTION_PERIOD) 
WHERE RETENTION_PERIOD != 'NEVER';

-- Create index for efficient call cleanup by timestamp
CREATE INDEX idx_answerbot_calls_started 
ON ANSWERBOT_CALLS (ABID, STARTED);

-- Optional: Set some existing bots to use monthly retention
-- Uncomment the following line if you want to enable retention for existing bots
-- UPDATE ANSWERBOT_SIP SET RETENTION_PERIOD = 'MONTH' WHERE RETENTION_PERIOD = 'NEVER';

-- Verify the migration
SELECT 
    'ANSWERBOT_SIP' as table_name,
    COUNT(*) as total_records,
    COUNT(CASE WHEN RETENTION_PERIOD = 'NEVER' THEN 1 END) as never_retention_count,
    COUNT(CASE WHEN RETENTION_PERIOD = 'MONTH' THEN 1 END) as month_retention_count,
    COUNT(CASE WHEN RETENTION_PERIOD != 'NEVER' THEN 1 END) as retention_enabled_count
FROM ANSWERBOT_SIP;
