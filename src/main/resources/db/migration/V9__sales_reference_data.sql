-- =====================================================================================
-- Phase 3 foundation (order-to-cash): the Deferred-COGS clearing account, the sales stock
-- movement types and their counter posting rules, and the sales document number sequences.
-- CUSTOMER location, the STANDARD tax rate and the customer partner flag already exist (V5).
-- =====================================================================================

-- ---- Deferred-COGS clearing account (asset; mirrors GR-IR 2150 on the sales side) ---
-- Shipment debits 1340 / credits Finished Goods; the invoice clears 1340 into COGS, so a
-- fully shipped-and-invoiced order leaves 1340 at zero.
INSERT INTO account (code, name, account_class, normal_balance, postable, currency_code, active)
VALUES ('1340', 'Deferred COGS (Shipped Not Invoiced)', 'ASSET', 'DEBIT', TRUE, 'TWD', TRUE);

-- ---- New stock movement types: sales shipment and customer return ------------------
-- Two CHECK constraints gate movement_type values: the subledger and the posting-rule table.
ALTER TABLE stock_ledger_entry DROP CONSTRAINT stock_ledger_entry_movement_type_check;
ALTER TABLE stock_ledger_entry ADD CONSTRAINT stock_ledger_entry_movement_type_check
    CHECK (movement_type IN ('RECEIPT', 'ISSUE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER',
                             'SHIPMENT', 'SALES_RETURN'));

ALTER TABLE inventory_posting_rule DROP CONSTRAINT inventory_posting_rule_movement_type_check;
ALTER TABLE inventory_posting_rule ADD CONSTRAINT inventory_posting_rule_movement_type_check
    CHECK (movement_type IN ('RECEIPT', 'ISSUE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER',
                             'SHIPMENT', 'SALES_RETURN'));

-- ---- Counter posting rules: shipment & return both clear against Deferred-COGS -------
INSERT INTO inventory_posting_rule (role, movement_type, account_code) VALUES
    ('COUNTER', 'SHIPMENT',     '1340'),
    ('COUNTER', 'SALES_RETURN', '1340');

-- ---- Sales document number sequences (unique-monotonic + prefix) --------------------
INSERT INTO number_sequence (scope, prefix, next_value, padding) VALUES
    ('SALES_ORDER',      'SO',  1, 6),
    ('DELIVERY',         'DLV', 1, 6),
    ('SALES_INVOICE',    'INV', 1, 6),
    ('CUSTOMER_RECEIPT', 'REC', 1, 6),
    ('CUSTOMER_RETURN',  'CRN', 1, 6);
