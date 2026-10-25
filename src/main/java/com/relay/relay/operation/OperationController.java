package com.relay.relay.operation;

import com.relay.relay.attempt.Attempt;
import com.relay.relay.attempt.AttemptResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/operations")
public class OperationController {

    private final OperationClaimService operationClaimService;

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
    public OperationResponse createOperation(
            @RequestHeader("Caller-Id") String callerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {

        return OperationResponse.from(
                operationService.createOperation(callerId, idempotencyKey)
        );
    }
    @GetMapping("/{id}")
    public OperationResponse getOperation(@PathVariable String id){
        return operationService.findOperation(id)
                .map(OperationResponse::from)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @GetMapping("/{id}/attempts")
    public List<AttemptResponse> getAttempts(@PathVariable String id){
        if(operationService.findOperation(id).isEmpty()){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return operationService.getAttempt(id)
                .stream()
                .map(AttemptResponse::from)
                .toList();
    }

}