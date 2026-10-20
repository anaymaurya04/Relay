package com.relay.relay.operation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OperationClaimService {

    private final JdbcTemplate jdbcTemplate;

    public OperationClaimService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Optional<String> claimNextOperation() {

        var ids = jdbcTemplate.query(
                """
                SELECT id
                FROM operations
                WHERE state = 'PENDING'
                ORDER BY id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> rs.getString("id")
        );

        if (ids.isEmpty()) {
            return Optional.empty();
        }

        String operationId = ids.get(0);

        jdbcTemplate.update(
                """
                UPDATE operations
                SET state = 'PROCESSING'
                WHERE id = ? AND state = 'PENDING'
                """,
                operationId
        );

        return Optional.of(operationId);
    }
}