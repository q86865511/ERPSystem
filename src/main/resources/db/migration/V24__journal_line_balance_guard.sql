-- =====================================================================================
-- Closes the one hole in "an unbalanced entry cannot be committed": V1's
-- journal_entry_balanced constraint trigger only fires on journal_entry, so appending a
-- line to an already-POSTED entry (journal_line INSERT is deliberately allowed there —
-- the posting flow inserts the header first) left the parent's balance unchecked. The
-- entry row is untouched by such an INSERT, so the deferred entry-level trigger never
-- re-fires. The application never takes that path (LedgerPostingService is the single
-- write point and never appends to an existing entry); this is defense in depth, so the
-- claim holds against direct SQL too.
--
-- Also deferred to COMMIT, so a posting transaction can still insert its header and its
-- lines in any order. Only equality is asserted here: the "non-zero total" rule stays
-- with the entry-level trigger, which is where an entry is created.
-- =====================================================================================
CREATE OR REPLACE FUNCTION assert_journal_line_parent_balanced() RETURNS TRIGGER AS $$
DECLARE
    parent_status VARCHAR(255);
    total_debit   NUMERIC(19, 4);
    total_credit  NUMERIC(19, 4);
BEGIN
    SELECT status INTO parent_status FROM journal_entry WHERE id = NEW.journal_entry_id;
    IF parent_status IS NULL OR parent_status NOT IN ('POSTED', 'REVERSED') THEN
        RETURN NEW;  -- draft entries are still being built; the entry trigger checks them on posting
    END IF;

    SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
      INTO total_debit, total_credit
      FROM journal_line
     WHERE journal_entry_id = NEW.journal_entry_id;

    IF total_debit <> total_credit THEN
        RAISE EXCEPTION 'journal entry % is unbalanced after line insert: debit=% credit=%',
            NEW.journal_entry_id, total_debit, total_credit;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER journal_line_parent_balanced
    AFTER INSERT ON journal_line
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION assert_journal_line_parent_balanced();
