-- Isolated fiscal years (2097 empty, 2098 loss, 2099 profit) for FiscalYearHardCloseIT. Kept separate from
-- the seeded 2026 year so locking them never disturbs other ITs sharing the Testcontainers database.
-- Idempotent (WHERE NOT EXISTS) so it is safe to re-run before every test method.
INSERT INTO fiscal_year (code, start_date, end_date, status)
SELECT y::text, make_date(y, 1, 1), make_date(y, 12, 31), 'OPEN'
FROM generate_series(2097, 2099) AS y
WHERE NOT EXISTS (SELECT 1 FROM fiscal_year f WHERE f.code = y::text);

INSERT INTO fiscal_period (fiscal_year_id, period_no, start_date, end_date, status)
SELECT f.id, m, make_date(y, m, 1), (make_date(y, m, 1) + INTERVAL '1 month' - INTERVAL '1 day')::date, 'OPEN'
FROM generate_series(2097, 2099) AS y
JOIN fiscal_year f ON f.code = y::text
CROSS JOIN generate_series(1, 12) AS m
WHERE NOT EXISTS (
    SELECT 1 FROM fiscal_period p WHERE p.fiscal_year_id = f.id AND p.period_no = m
);
