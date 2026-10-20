package com.relay.relay.worker;

import ch.qos.logback.core.util.FixedDelay;
import com.relay.relay.operation.Operation;
import com.relay.relay.operation.OperationRepository;
import com.relay.relay.operation.OperationState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OperationWorker {
    private  final OperationRepository operationRepository;

    public OperationWorker(OperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    //@Scheduled(fixedDelay = 5000)
    public void processPendingOperation(){
        List<Operation> operations = operationRepository.findByState(OperationState.PENDING);

        for (Operation operation:operations){
            process(operation);
        }
    }

    private void process(Operation operation) {
        System.out.println("Processing Operation" + operation.getId());
        operation.transitionTo(OperationState.PROCESSING);
        operationRepository.save(operation);
    }
}
