package com.relay.relay.worker;

import com.relay.relay.attempt.Attempt;
import com.relay.relay.attempt.AttemptRepository;
import com.relay.relay.operation.Operation;
import com.relay.relay.operation.OperationClaimService;
import com.relay.relay.operation.OperationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(
        name = "relay.worker.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OperationWorker {
    private  final OperationRepository operationRepository;
    private final AttemptRepository attemptRepository;
    private final OperationClaimService claimService;
    public OperationWorker(OperationRepository operationRepository, AttemptRepository attemptRepository, OperationClaimService claimService) {
        this.operationRepository = operationRepository;
        this.attemptRepository = attemptRepository;
        this.claimService = claimService;
    }

    @Scheduled(fixedDelay = 5000)
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


}
