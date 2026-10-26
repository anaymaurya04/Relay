package com.relay.relay.operation;

import com.relay.relay.attempt.AttemptResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/operations")
public class OperationController {

    private final OperationService operationService;

    public OperationController(OperationService operationService) {
        this.operationService = operationService;
    }

    @PostMapping
    public OperationResponse createOperation(
            @RequestHeader("Caller-Id") String callerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateOperationRequest request
    ) {
        return OperationResponse.from(
                operationService.createOperation(
                        callerId,
                        idempotencyKey,
                        request
                )
        );
    }

    @GetMapping("/{id}")
    public OperationResponse getOperation(@PathVariable String id) {
        return operationService.findOperation(id)
                .map(OperationResponse::from)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{id}/attempts")
    public List<AttemptResponse> getAttempts(@PathVariable String id) {
        if (operationService.findOperation(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return operationService.getAttempts(id)
                .stream()
                .map(AttemptResponse::from)
                .toList();
    }
}
