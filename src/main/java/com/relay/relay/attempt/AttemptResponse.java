package com.relay.relay.attempt;

import java.time.Instant;

public record AttemptResponse(
        String id,
        int attemptNumber,
        AttemptState state,
        Instant startedAt,
        Instant finishedAt,
        Integer responseCode,
        String error
) {
    public  static AttemptResponse from(Attempt attempt){
        return  new AttemptResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getState(),
                attempt.getStartedAt(),
                attempt.getFinishedAt(),
                attempt.getResponseCode(),
                attempt.getError()
        );
    }
}
