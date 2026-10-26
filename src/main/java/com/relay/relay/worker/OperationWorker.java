package com.relay.relay.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The only scheduled part of the worker. All real work lives in
 * OperationProcessor so it can be tested without the clock.
 */
@Component
@ConditionalOnProperty(
        name = "relay.worker.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OperationWorker {

    private final OperationProcessor operationProcessor;

    public OperationWorker(OperationProcessor operationProcessor) {
        this.operationProcessor = operationProcessor;
    }

    @Scheduled(fixedDelayString = "${relay.worker.poll-interval-ms:5000}")
    public void processPendingOperations() {
        operationProcessor.processAll();
    }
}
