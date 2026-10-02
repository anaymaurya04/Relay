package com.relay.relay.operation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OperationRepository
        extends JpaRepository<Operation, String> {

    Optional<Operation> findByCallerIdAndIdempotencyKey(
            String callerId,
            String idempotencyKey
    );
}