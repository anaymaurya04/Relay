package com.relay.relay.execution;

import com.relay.relay.attempt.AttemptOutcome;
import com.relay.relay.attempt.AttemptState;
import com.relay.relay.operation.OperationState;

/**
 * Maps one HTTP result to the states stored in the database.
 *
 * Phase 3 records RETRY_WAITING but does not retry yet. Phase 4 will claim
 * those operations when their next attempt time arrives.
 */
public final class OutcomeClassifier {

    private OutcomeClassifier() {
    }

    public static AttemptOutcome classify(HttpCall call) {

        if (call.failure() != null) {
            AttemptState attemptState = switch (call.failure()) {
                // The request provably never left this machine.
                case CONNECT_TIMEOUT, NOT_SENT -> AttemptState.FAILED;
                // The request may have arrived; the outcome is genuinely unknown.
                case REQUEST_TIMEOUT, AMBIGUOUS -> AttemptState.UNKNOWN;
            };
            return new AttemptOutcome(
                    attemptState,
                    OperationState.RETRY_WAITING,
                    null,
                    call.error()
            );
        }

        if (call.statusCode() == null) {
            throw new IllegalArgumentException(
                    "HttpCall must have a response or a failure"
            );
        }

        int status = call.statusCode();

        if (status >= 200 && status < 300) {
            return new AttemptOutcome(
                    AttemptState.SUCCEEDED,
                    OperationState.SUCCESS,
                    status,
                    null
            );
        }

        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return new AttemptOutcome(
                    AttemptState.FAILED,
                    OperationState.RETRY_WAITING,
                    status,
                    "HTTP " + status
            );
        }

        // Every other 3xx or 4xx is permanent. Redirects are not followed,
        // so a 3xx means the caller pointed at the wrong URL.
        return new AttemptOutcome(
                AttemptState.FAILED,
                OperationState.FAILED,
                status,
                "HTTP " + status
        );
    }
}
