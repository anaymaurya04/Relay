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
                        columnNames = {"caller_id", "idempotency_key"}
                )
        })
public class Operation {

    @Id
    private String id;

    @Column(name = "caller_id", nullable = false)
    private String callerId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "target_url", nullable = false, columnDefinition = "TEXT")
    private String targetUrl;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Enumerated(EnumType.STRING)
    private OperationState state;

    public Operation(
            String id,
            String callerId,
            String idempotencyKey,
            String targetUrl,
            String httpMethod,
            String requestBody
    ) {
        this.id = id;
        this.callerId = callerId;
        this.idempotencyKey = idempotencyKey;
        this.targetUrl = targetUrl;
        this.httpMethod = httpMethod;
        this.requestBody = requestBody;
        this.state = OperationState.PENDING;
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
                    || next == OperationState.FAILED
                    || next == OperationState.RETRY_WAITING;

            case SUCCESS, FAILED -> false;
        };
    }
}
