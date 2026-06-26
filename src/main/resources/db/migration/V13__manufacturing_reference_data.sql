-- =====================================================================================
-- Phase 4 foundation (manufacturing): the WIP stock movement types and their counter posting rules,
-- and the work-order document number sequence. The WIP (PRODUCTION_WIP) and SCRAP locations and the
-- WIP control account (1320) and Manufacturing Variance account (5930) already exist.
-- =====================================================================================

-- ---- New stock movement types: WIP issue / completion / return ---------------------
ALTER TABLE stock_ledger_entry DROP CONSTRAINT stock_ledger_entry_movement_type_check;
ALTER TABLE stock_ledger_entry ADD CONSTRAINT stock_ledger_entry_movement_type_check
    CHECK (movement_type IN ('RECEIPT', 'ISSUE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER',
                             'SHIPMENT', 'SALES_RETURN',
                             'MANUFACTURING_ISSUE', 'MANUFACTURING_RECEIPT', 'MANUFACTURING_RETURN'));

ALTER TABLE inventory_posting_rule DROP CONSTRAINT inventory_posting_rule_movement_type_check;
ALTER TABLE inventory_posting_rule ADD CONSTRAINT inventory_posting_rule_movement_type_check
    CHECK (movement_type IN ('RECEIPT', 'ISSUE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER',
                             'SHIPMENT', 'SALES_RETURN',
                             'MANUFACTURING_ISSUE', 'MANUFACTURING_RECEIPT', 'MANUFACTURING_RETURN'));

-- ---- Counter posting rules: all WIP movements clear against the WIP control account --
-- Issue debits 1320 (raw out of stock); completion credits 1320 (finished into stock); a cancel
-- return credits 1320 (raw back into stock). WIP nets to zero once a work order completes.
INSERT INTO inventory_posting_rule (role, movement_type, account_code) VALUES
    ('COUNTER', 'MANUFACTURING_ISSUE',   '1320'),
    ('COUNTER', 'MANUFACTURING_RECEIPT', '1320'),
    ('COUNTER', 'MANUFACTURING_RETURN',  '1320');

-- ---- Work-order document number sequence -------------------------------------------
INSERT INTO number_sequence (scope, prefix, next_value, padding) VALUES
    ('WORK_ORDER', 'WO', 1, 6);
