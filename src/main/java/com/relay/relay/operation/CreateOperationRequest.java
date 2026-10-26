package com.relay.relay.operation;

/**
 * Request body for POST /operations.
 *
 * requestBody is optional. It is forwarded to the destination exactly as given
 * and is never returned by the read API.
 */
public record CreateOperationRequest(
        String targetUrl,
        String httpMethod,
        String requestBody
) {
}
