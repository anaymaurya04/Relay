package com.relay.relay.operation;

import com.relay.relay.attempt.Attempt;
import com.relay.relay.attempt.AttemptRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class OperationService {

    private final AttemptRepository attemptRepository;
    private final OperationRepository operationRepository;
    private final OperationWriter operationWriter;
    private final DestinationPolicy destinationPolicy;

    public OperationService(
            AttemptRepository attemptRepository,
            OperationRepository operationRepository,
            OperationWriter operationWriter,
            DestinationPolicy destinationPolicy
    ) {
        this.attemptRepository = attemptRepository;
        this.operationRepository = operationRepository;
        this.operationWriter = operationWriter;
        this.destinationPolicy = destinationPolicy;
    }

    /**
     * Returns the operation for this caller/key pair, creating it once.
     *
     * Two requests with the same key can both miss the fast read and both try
     * to insert. PostgreSQL lets only one insert win; the loser catches the
     * duplicate-key error, re-reads, and returns the winner's row.
     */
    public Operation createOperation(
            String callerId,
            String idempotencyKey,
            CreateOperationRequest request
    ) {
        destinationPolicy.validate(
                request.targetUrl(),
                request.httpMethod(),
                request.requestBody()
        );

        Optional<Operation> existing = findExisting(callerId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Operation operation = new Operation(
                UUID.randomUUID().toString(),
                callerId,
                idempotencyKey,
                request.targetUrl(),
                request.httpMethod().toUpperCase(Locale.ROOT),
                request.requestBody()
        );

        try {
            return operationWriter.insertNew(operation);
        } catch (DataIntegrityViolationException duplicate) {
            return findExisting(callerId, idempotencyKey)
                    .orElseThrow(() -> duplicate);
        }
    }

    public Optional<Operation> findOperation(String id) {
        return operationRepository.findById(id);
    }

    public List<Attempt> getAttempts(String operationId) {
        return attemptRepository
                .findByOperation_IdOrderByAttemptNumberAsc(operationId);
    }

    private Optional<Operation> findExisting(
            String callerId,
            String idempotencyKey
    ) {
        return operationRepository.findByCallerIdAndIdempotencyKey(
                callerId,
                idempotencyKey
        );
    }
}
