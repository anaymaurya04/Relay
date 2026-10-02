package com.relay.relay.operation;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/operations")
public class OperationController {

    private final OperationService operationService;

    public OperationController(OperationService operationService) {
        this.operationService = operationService;
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