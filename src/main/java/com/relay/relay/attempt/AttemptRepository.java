package com.relay.relay.attempt;

import java.util.List;

public interface AttemptRepository {
    List<Attempt> findOperationIdByAttemptNumber(String operationId);
}
