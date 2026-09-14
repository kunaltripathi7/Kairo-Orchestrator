-- DAG Execution Support Migration
-- Run this against the 'kairo' database before restarting services

-- Add new columns
ALTER TABLE tasks ADD COLUMN name VARCHAR(100);
ALTER TABLE tasks ADD COLUMN depends_on JSONB DEFAULT '[]';

-- Backfill existing tasks with generated names
UPDATE tasks SET name = 'task-' || sequence_number WHERE name IS NULL;

-- Add NOT NULL constraint after backfill
ALTER TABLE tasks ALTER COLUMN name SET NOT NULL;

-- Unique name within a workflow
ALTER TABLE tasks ADD CONSTRAINT uq_task_name_per_workflow UNIQUE (workflow_id, name);
