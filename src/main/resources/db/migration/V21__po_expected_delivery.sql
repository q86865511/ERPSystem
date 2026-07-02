-- =====================================================================================
-- Supplier on-time performance (C3): purchase-order lines gain an expected delivery date, which the
-- supplier-performance report compares against each goods receipt's actual posting date.
-- =====================================================================================

ALTER TABLE po_line ADD COLUMN expected_delivery_date DATE;
