package com.relay.relay.attempt;

import com.relay.relay.operation.Operation;
import com.relay.relay.operation.OperationRepository;
import com.relay.relay.operation.OperationState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the two short transactions around one HTTP call:
 *
 *   begin()  commits Attempt #N as RUNNING before the network call.
 *   finish() records the result in a separate transaction after the call.
 *
 * Do not merge them. If the RUNNING attempt is not committed first, a crash
 * mid-call leaves no evidence that the request may have been sent.
 */
@Service
public class AttemptLifecycle {

    private final AttemptRepository attemptRepository;
    private final OperationRepository operationRepository;

    public AttemptLifecycle(
            AttemptRepository attemptRepository,
            OperationRepository operationRepository
    ) {
        this.attemptRepository = attemptRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public StartedAttempt begin(String operationId) {
        Operation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Operation not found: " + operationId
                ));

        int attemptNumber =
                (int) attemptRepository.countByOperation_Id(operationId) + 1;

        Attempt attempt = new Attempt(operation, attemptNumber);
        attemptRepository.saveAndFlush(attempt);

        return new StartedAttempt(attempt.getId(), attemptNumber);
    }

    @Transactional
    public void finish(String attemptId, AttemptOutcome outcome) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalStateException(
                        "Attempt not found: " + attemptId
                ));

        applyAttemptResult(attempt, outcome);

        Operation operation = attempt.getOperation();

        // If another worker already moved this operation, do not overwrite it.
        // Real fencing tokens arrive after the first demo.
        if (operation.getState() != OperationState.PROCESSING) {
            return;
        }

        operation.transitionTo(outcome.operationState());
    }

    private void applyAttemptResult(Attempt attempt, AttemptOutcome outcome) {
        switch (outcome.attemptState()) {
            case SUCCEEDED -> attempt.markSucceeded(outcome.responseCode());
            case FAILED -> attempt.markFailed(outcome.responseCode(), outcome.error());
            case UNKNOWN -> attempt.markUnknown(outcome.error());
            case RUNNING -> throw new IllegalArgumentException(
                    "An attempt cannot finish in RUNNING state"
            );
        }
    }

    public record StartedAttempt(String id, int attemptNumber) {
    }
}
