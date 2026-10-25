package com.relay.relay.attempt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface AttemptRepository extends JpaRepository<Attempt, String> {
    List<Attempt> findByOperation_IdOrderByAttemptNumberAsc(String operationId);
}
