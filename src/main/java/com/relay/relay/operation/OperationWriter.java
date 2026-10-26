package com.relay.relay.operation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts one operation in its own transaction.
 *
 * This must be a separate bean and a separate transaction: when the unique
 * constraint rejects a duplicate insert, that transaction has to end before
 * OperationService re-reads the row the winner wrote.
 */
@Service
public class OperationWriter {

    private final OperationRepository operationRepository;

    public OperationWriter(OperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Operation insertNew(Operation operation) {
        return operationRepository.saveAndFlush(operation);
    }
}
