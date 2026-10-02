package com.relay.relay.attempt;

import com.relay.relay.operation.Operation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "attempts")
public class Attempt {
    @Id
    private String  id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false)

    @Column(name = "attempt_number", nullable = false)
    private Operation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private int attemptNumber;

    private AttemptState state;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private Integer responseCode;

    @Column(columnDefinition = "TEXT")
    private String error;

    public Attempt(Operation operation, int attemptNumber) {
        this.id = UUID.randomUUID().toString();
        this.operation = operation;
        this.attemptNumber = attemptNumber;
        this.state = AttemptState.RUNNING;
        this.startedAt = Instant.now();
    }

    public void MarkSucceeded(Integer responseCode){
        this.state = AttemptState.SUCCEEDED;
        this.responseCode = responseCode;
        this.finishedAt = Instant.now();
    }
    public void MarkFailed(Integer responseCode, String error){
        this.state = AttemptState.FAILED;
        this.responseCode = responseCode;
        this.error = error;
        this.finishedAt =  Instant.now();
    }
}
