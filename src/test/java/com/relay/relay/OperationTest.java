package com.relay.relay;

import com.relay.relay.operation.Operation;
import com.relay.relay.operation.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationTest {
    @Test
    void operationCannotMoveFromSuccessToPending() {

        Operation operation = new Operation("OP-123");

        operation.transitionTo(OperationState.PROCESSING);
        operation.transitionTo(OperationState.SUCCESS);

        assertThrows(
                IllegalStateException.class,
                () -> operation.transitionTo(OperationState.PENDING)
        );
    }

    @Test
    void operationCanSuccessfullyComplete() {

        Operation operation = new Operation("OP-123");

        assertEquals(
                OperationState.PENDING,
                operation.getState()
        );

        operation.transitionTo(OperationState.PROCESSING);

        assertEquals(
                OperationState.PROCESSING,
                operation.getState()
        );

        operation.transitionTo(OperationState.SUCCESS);

        assertEquals(
                OperationState.SUCCESS,
                operation.getState()
        );
    }
}