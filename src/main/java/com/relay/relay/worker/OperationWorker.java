package com.relay.relay.worker;

import ch.qos.logback.core.util.FixedDelay;
import com.relay.relay.attempt.Attempt;
import com.relay.relay.attempt.AttemptRepository;
import com.relay.relay.operation.Operation;
import com.relay.relay.operation.OperationClaimService;
import com.relay.relay.operation.OperationRepository;
import com.relay.relay.operation.OperationState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OperationWorker {
    private  final OperationRepository operationRepository;
    private final AttemptRepository attemptRepository;
    private final OperationClaimService claimService;
    public OperationWorker(OperationRepository operationRepository, AttemptRepository attemptRepository, OperationClaimService claimService) {
        this.operationRepository = operationRepository;
        this.attemptRepository = attemptRepository;
        this.claimService = claimService;
    }

    //@Scheduled(fixedDelay = 5000)
    public void processPendingOperation(){
        while (true){
            Optional<String> claimed = claimService.claimNextOperation();
            if (claimed.isEmpty()){
                break;
            }
            Operation operation = operationRepository.findById(claimed.get())
                    .orElseThrow();
            attemptRepository.save(new Attempt(operation,1));
        }

    }

    private void process(Operation operation) {
        System.out.println("Processing Operation" + operation.getId());
        operation.transitionTo(OperationState.PROCESSING);
        operationRepository.save(operation);
    }
}
