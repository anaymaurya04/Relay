package com.relay.relay.execution;

import com.relay.relay.operation.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends one HTTP request for one operation.
 *
 * This class has no database code and no transaction. The caller must commit
 * the RUNNING attempt before calling execute(), otherwise a crash could lose
 * the only record that a request might have been sent.
 */
@Component
public class HttpOperationExecutor {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpOperationExecutor(
            @Value("${relay.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${relay.http.request-timeout:5s}") Duration requestTimeout
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                // A redirect is a different destination; do not follow silently.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.requestTimeout = requestTimeout;
    }

    public HttpCall execute(Operation operation) {
        HttpRequest request = buildRequest(operation);

        try {
            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );
            return HttpCall.response(response.statusCode());
        } catch (HttpTimeoutException timeout) {
            return HttpCall.timeout(
                    "timeout after " + requestTimeout.toMillis() + "ms"
            );
        } catch (IOException networkError) {
            return HttpCall.failure(
                    networkError.getClass().getSimpleName()
                            + ": "
                            + networkError.getMessage()
            );
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return HttpCall.failure("interrupted while waiting for response");
        }
    }

    private HttpRequest buildRequest(Operation operation) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(operation.getTargetUrl())
                )
                .timeout(requestTimeout)
                .method(
                        operation.getHttpMethod(),
                        bodyPublisher(operation.getRequestBody())
                );

        if (operation.getRequestBody() != null) {
            builder.header("Content-Type", "application/json");
        }

        return builder.build();
    }

    private static HttpRequest.BodyPublisher bodyPublisher(String requestBody) {
        if (requestBody == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(
                requestBody,
                StandardCharsets.UTF_8
        );
    }
}
