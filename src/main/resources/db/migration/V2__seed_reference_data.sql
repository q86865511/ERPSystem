-- =====================================================================================
-- Reference data: the single GENERAL journal, the journal-entry number sequence, an open
-- fiscal year with twelve monthly periods, and the canonical chart of accounts (TWD).
-- =====================================================================================

INSERT INTO journal (code, name, type) VALUES ('GEN', 'General Journal', 'GENERAL');

INSERT INTO number_sequence (scope, prefix, next_value, padding)
VALUES ('JOURNAL_ENTRY', 'JE', 1, 6);

-- Fiscal year 2026 with twelve OPEN monthly periods.
INSERT INTO fiscal_year (code, start_date, end_date, status)
VALUES ('2026', DATE '2026-01-01', DATE '2026-12-31', 'OPEN');

INSERT INTO fiscal_period (fiscal_year_id, period_no, start_date, end_date, status)
SELECT fy.id,
       m,
       (DATE '2026-01-01' + ((m - 1) || ' month')::interval)::date AS start_date,
       ((DATE '2026-01-01' + (m || ' month')::interval) - INTERVAL '1 day')::date AS end_date,
       'OPEN'
FROM fiscal_year fy
CROSS JOIN generate_series(1, 12) AS m
WHERE fy.code = '2026';

-- Canonical chart of accounts (single currency: TWD).
INSERT INTO account (code, name, account_class, normal_balance, postable, currency_code, active) VALUES
    ('1010', 'Cash / Bank',                        'ASSET',     'DEBIT',  TRUE, 'TWD', TRUE),
    ('1200', 'Accounts Receivable',                'ASSET',     'DEBIT',  TRUE, 'TWD', TRUE),
    ('1310', 'Inventory - Raw Materials',          'ASSET',     'DEBIT',  TRUE, 'TWD', TRUE),
    ('1320', 'Work-In-Process (WIP)',              'ASSET',     'DEBIT',  TRUE, 'TWD', TRUE),
    ('1330', 'Inventory - Finished Goods',         'ASSET',     'DEBIT',  TRUE, 'TWD', TRUE),
    ('1450', 'Input VAT',                          'ASSET',     'DEBIT',  TRUE, 'TWD', TRUE),
    ('2100', 'Accounts Payable',                   'LIABILITY', 'CREDIT', TRUE, 'TWD', TRUE),
    ('2150', 'GR-IR Clearing',                     'LIABILITY', 'CREDIT', TRUE, 'TWD', TRUE),
    ('2400', 'Output VAT Payable',                 'LIABILITY', 'CREDIT', TRUE, 'TWD', TRUE),
    ('3100', 'Share Capital',                      'EQUITY',    'CREDIT', TRUE, 'TWD', TRUE),
    ('3200', 'Retained Earnings',                  'EQUITY',    'CREDIT', TRUE, 'TWD', TRUE),
    ('4100', 'Sales Revenue',                      'REVENUE',   'CREDIT', TRUE, 'TWD', TRUE),
    ('5100', 'COGS',                               'EXPENSE',   'DEBIT',  TRUE, 'TWD', TRUE),
    ('5930', 'Manufacturing Variance',             'EXPENSE',   'DEBIT',  TRUE, 'TWD', TRUE),
    ('6000', 'Inventory Adjustment / Loss',        'EXPENSE',   'DEBIT',  TRUE, 'TWD', TRUE),
    ('9990', 'Rounding Difference',                'EXPENSE',   'DEBIT',  TRUE, 'TWD', TRUE);
