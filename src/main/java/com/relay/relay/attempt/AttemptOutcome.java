package com.relay.relay.attempt;

import com.relay.relay.operation.OperationState;

/**
 * What one attempt means for the attempt row and for its operation.
 */
public record AttemptOutcome(
        AttemptState attemptState,
        OperationState operationState,
        Integer responseCode,
        String error
) {
}
