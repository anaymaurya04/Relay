package com.relay.relay.operation;

public record OperationResponse(String id, OperationState state) {
    public static OperationResponse from(Operation operation) {
        return new OperationResponse(
                operation.getId(),
                operation.getState()
        );
    }
}
