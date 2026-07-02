-- =====================================================================================
-- Work-order scheduling (C4): a planned production window per work order, rendered as the
-- manufacturing schedule Gantt. Purely informational — it drives no posting.
-- =====================================================================================

ALTER TABLE work_order ADD COLUMN planned_start DATE;
ALTER TABLE work_order ADD COLUMN planned_end DATE;
