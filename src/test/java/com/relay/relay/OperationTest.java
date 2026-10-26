package com.relay.relay;

import com.relay.relay.operation.Operation;
import com.relay.relay.operation.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationTest {

    private static Operation newOperation() {
        return new Operation(
                "OP-123",
                "caller-1",
                "key1",
                "http://localhost:9090/ok",
                "POST",
                null
        );
    }

    @Test
    void operationCannotMoveFromSuccessToPending() {
        Operation operation = newOperation();

        operation.transitionTo(OperationState.PROCESSING);
        operation.transitionTo(OperationState.SUCCESS);

        assertThrows(
                IllegalStateException.class,
                () -> operation.transitionTo(OperationState.PENDING)
        );
    }

    @Test
    void operationCanSuccessfullyComplete() {
        Operation operation = newOperation();
        assertEquals(OperationState.PENDING, operation.getState());

        operation.transitionTo(OperationState.PROCESSING);
        assertEquals(OperationState.PROCESSING, operation.getState());

        operation.transitionTo(OperationState.SUCCESS);
        assertEquals(OperationState.SUCCESS, operation.getState());
    }

    @Test
    void operationCanFailPermanently() {
        Operation operation = newOperation();

        operation.transitionTo(OperationState.PROCESSING);
        operation.transitionTo(OperationState.FAILED);

        assertEquals(OperationState.FAILED, operation.getState());
        assertThrows(
                IllegalStateException.class,
                () -> operation.transitionTo(OperationState.PROCESSING)
        );
    }

    @Test
    void operationCanWaitForRetryAndBeClaimedAgain() {
        Operation operation = newOperation();

        operation.transitionTo(OperationState.PROCESSING);
        operation.transitionTo(OperationState.RETRY_WAITING);
        operation.transitionTo(OperationState.PROCESSING);

        assertEquals(OperationState.PROCESSING, operation.getState());
    }
}
