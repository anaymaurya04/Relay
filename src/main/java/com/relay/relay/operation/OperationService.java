package com.relay.relay.operation;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OperationService {

    private final OperationRepository operationRepository;

    public OperationService(OperationRepository operationRepository) {
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
}