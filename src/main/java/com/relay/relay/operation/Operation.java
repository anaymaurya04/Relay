package com.relay.relay.operation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "operations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_caller_idempotency",
                        columnNames = {"callerId", "idempotencyKey"}
                )
        })
public class Operation {

    @Id
    private String id;
    @Column(name = "caller_id", nullable = false)
    private String callerId;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    private OperationState state;

    public Operation(String id, String callerId, String idempotencyKey) {
        this.id = id;
        this.state = OperationState.PENDING;
        this.callerId = callerId;
        this.idempotencyKey = idempotencyKey;
    }

    public void transitionTo(OperationState newState) {

        if (!isValidTransition(this.state, newState)) {
            throw new IllegalStateException(
                    "Invalid transition: "
                            + this.state
                            + " -> "
                            + newState
            );
        }

        this.state = newState;
    }

    private boolean isValidTransition(
            OperationState current,
            OperationState next) {

        return switch (current) {

            case PENDING, RETRY_WAITING -> next == OperationState.PROCESSING;

            case PROCESSING -> next == OperationState.SUCCESS
                    || next == OperationState.RETRY_WAITING;

            case SUCCESS -> false;
        };
    }
}