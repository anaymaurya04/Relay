package com.relay.relay.worker;

import com.relay.relay.attempt.AttemptLifecycle;
import com.relay.relay.attempt.AttemptOutcome;
import com.relay.relay.attempt.AttemptState;
import com.relay.relay.execution.HttpCall;
import com.relay.relay.execution.HttpOperationExecutor;
import com.relay.relay.execution.OutcomeClassifier;
import com.relay.relay.operation.DestinationPolicy;
import com.relay.relay.operation.Operation;
import com.relay.relay.operation.OperationClaimService;
import com.relay.relay.operation.OperationRepository;
import com.relay.relay.operation.OperationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Claims due operations and runs one HTTP attempt for each.
 *
 * Kept separate from OperationWorker so tests can call processAll() directly
 * instead of waiting for the scheduler.
 */
@Component
public class OperationProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(OperationProcessor.class);

    private final OperationRepository operationRepository;
    private final OperationClaimService claimService;
    private final DestinationPolicy destinationPolicy;
    private final AttemptLifecycle attemptLifecycle;
    private final HttpOperationExecutor httpExecutor;

    public OperationProcessor(
            OperationRepository operationRepository,
            OperationClaimService claimService,
            DestinationPolicy destinationPolicy,
            AttemptLifecycle attemptLifecycle,
            HttpOperationExecutor httpExecutor
    ) {
        this.operationRepository = operationRepository;
        this.claimService = claimService;
        this.destinationPolicy = destinationPolicy;
        this.attemptLifecycle = attemptLifecycle;
        this.httpExecutor = httpExecutor;
    }

    public void processAll() {
        while (true) {
            Optional<String> claimed = claimService.claimNextOperation();
            if (claimed.isEmpty()) {
                return;
            }
            process(claimed.get());
        }
    }

    private void process(String operationId) {
        Operation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed operation disappeared: " + operationId
                ));

        AttemptLifecycle.StartedAttempt attempt =
                attemptLifecycle.begin(operationId);

        AttemptOutcome outcome = executeSafely(operation);

        attemptLifecycle.finish(attempt.id(), outcome);

        log.info(
                "operation {} attempt {} -> {}",
                operationId,
                attempt.attemptNumber(),
                outcome.operationState()
        );
    }

    private AttemptOutcome executeSafely(Operation operation) {
        try {
            // Validate again: the allowlist may have changed since creation.
            destinationPolicy.validate(
                    operation.getTargetUrl(),
                    operation.getHttpMethod(),
                    operation.getRequestBody()
            );

            HttpCall call = httpExecutor.execute(operation);
            return OutcomeClassifier.classify(call);

        } catch (IllegalArgumentException invalidDestination) {
            // A bad destination can never succeed, so do not schedule a retry.
            return new AttemptOutcome(
                    AttemptState.FAILED,
                    OperationState.FAILED,
                    null,
                    "invalid destination: " + invalidDestination.getMessage()
            );
        }
    }
}
