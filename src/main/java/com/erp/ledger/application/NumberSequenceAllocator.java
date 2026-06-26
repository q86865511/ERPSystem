package com.erp.ledger.application;

import com.erp.ledger.api.SequenceAllocator;
import com.erp.ledger.domain.NumberSequence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the {@link SequenceAllocator} port over {@link NumberSequence}. Takes a pessimistic write
 * lock on the scope's counter row, advances it, and returns the formatted number. Runs in the caller's
 * transaction so the allocated number commits or rolls back with the document that used it.
 */
@Service
public class NumberSequenceAllocator implements SequenceAllocator {

    private final NumberSequenceRepository numberSequenceRepository;

    public NumberSequenceAllocator(NumberSequenceRepository numberSequenceRepository) {
        this.numberSequenceRepository = numberSequenceRepository;
    }

    @Override
    @Transactional
    public String next(String scope) {
        NumberSequence sequence = numberSequenceRepository.lockByScope(scope)
                .orElseThrow(() -> new IllegalStateException("missing number sequence: " + scope));
        return sequence.format(sequence.take());
    }
}
