package com.relay.relay.operation;

public record CreateOperationRequest(
        String callerId,
        String idempotencyKey
) {
}