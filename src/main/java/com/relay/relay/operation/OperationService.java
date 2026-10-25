package com.relay.relay.operation;

import com.relay.relay.attempt.Attempt;
import com.relay.relay.attempt.AttemptRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OperationService {

    private final AttemptRepository attemptRepository;
    private final OperationRepository operationRepository;

    public OperationService(AttemptRepository attemptRepository, OperationRepository operationRepository) {
        this.attemptRepository = attemptRepository;
        this.operationRepository = operationRepository;
    }

    public Operation createOperation(
            String callerId,
            String idempotencyKey
    ) {

        return operationRepository
                .findByCallerIdAndIdempotencyKey(
                        callerId,
                        idempotencyKey
                )
                .orElseGet(() -> {

                    String operationId =
                            UUID.randomUUID().toString();

                    Operation operation = new Operation(
                            operationId,
                            callerId,
                            idempotencyKey
                    );

                    return operationRepository.save(operation);
                });
    }

    public Optional<Operation> findOperation(String id){
        return operationRepository.findById(id);
    }
    public List<Attempt> getAttempt(String id){
        return attemptRepository
                .findByOperation_IdOrderByAttemptNumberAsc(id);
    }
}