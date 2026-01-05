-- Migration: Add composite index for duplicate detection optimization
-- Date: 2025-01-05
-- Description: Adds a composite index on tracking_sessions (user_id, start_date_time DESC)
--              to optimize duplicate session detection queries.
--
-- This index supports the duplicate detection feature which queries sessions by user_id
-- and time range to find potential duplicate uploads.
--
-- Run this migration on existing databases:
-- psql -U geotracker -d geotracker -f migration_add_duplicate_detection_index.sql

-- Check if index already exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
        AND tablename = 'tracking_sessions'
        AND indexname = 'idx_sessions_user_start_time'
    ) THEN
        -- Create composite index for duplicate detection
        CREATE INDEX idx_sessions_user_start_time
        ON public.tracking_sessions USING btree (user_id, start_date_time DESC);

        RAISE NOTICE 'Index idx_sessions_user_start_time created successfully';
    ELSE
        RAISE NOTICE 'Index idx_sessions_user_start_time already exists, skipping creation';
    END IF;
END $$;

-- Analyze table to update statistics
ANALYZE public.tracking_sessions;
ANALYZE public.gps_tracking_points;

-- Display index information
SELECT
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
AND tablename = 'tracking_sessions'
ORDER BY indexname;
