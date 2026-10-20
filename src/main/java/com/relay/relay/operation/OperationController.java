package com.relay.relay.operation;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/operations")
public class OperationController {

    private OperationClaimService operationClaimService;

    private final OperationService operationService;

    public OperationController(OperationService operationService,OperationClaimService operationClaimService) {
        this.operationService = operationService;
        this.operationClaimService = operationClaimService;
    }

    @PostMapping("/claim-next")
    public String claimNextOperation() {
        return operationClaimService.claimNextOperation()
                .orElse("No pending operations");
    }

    @PostMapping
    public Operation createOperation(
            @RequestHeader("Caller-Id") String callerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {

        return operationService.createOperation(
                callerId,
                idempotencyKey
        );
    }
}