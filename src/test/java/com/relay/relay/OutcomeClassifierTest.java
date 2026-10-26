package com.relay.relay;

import com.relay.relay.attempt.AttemptOutcome;
import com.relay.relay.attempt.AttemptState;
import com.relay.relay.execution.HttpCall;
import com.relay.relay.execution.OutcomeClassifier;
import com.relay.relay.operation.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeClassifierTest {

    @Test
    void connectTimeoutIsFailedAndRetryable() {
        AttemptOutcome outcome = OutcomeClassifier.classify(
                HttpCall.connectTimeout("connect timeout after 300ms")
        );

        assertEquals(AttemptState.FAILED, outcome.attemptState());
        assertEquals(OperationState.RETRY_WAITING, outcome.operationState());
        assertNull(outcome.responseCode());
        assertTrue(outcome.error().contains("connect"));
    }

    @Test
    void requestTimeoutIsUnknownAndRetryable() {
        AttemptOutcome outcome = OutcomeClassifier.classify(
                HttpCall.requestTimeout("request timeout after 500ms")
        );

        assertEquals(AttemptState.UNKNOWN, outcome.attemptState());
        assertEquals(OperationState.RETRY_WAITING, outcome.operationState());
        assertNull(outcome.responseCode());
    }

    @Test
    void notSentIsFailedAndRetryable() {
        AttemptOutcome outcome = OutcomeClassifier.classify(
                HttpCall.notSent("ConnectException: Connection refused")
        );

        assertEquals(AttemptState.FAILED, outcome.attemptState());
        assertEquals(OperationState.RETRY_WAITING, outcome.operationState());
        assertNull(outcome.responseCode());
    }

    @Test
    void ambiguousFailureIsUnknownAndRetryable() {
        AttemptOutcome outcome = OutcomeClassifier.classify(
                HttpCall.ambiguous("IOException: Connection reset")
        );

        assertEquals(AttemptState.UNKNOWN, outcome.attemptState());
        assertEquals(OperationState.RETRY_WAITING, outcome.operationState());
        assertNull(outcome.responseCode());
    }
}
