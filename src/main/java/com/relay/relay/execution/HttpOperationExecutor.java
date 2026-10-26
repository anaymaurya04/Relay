package com.relay.relay.execution;

import com.relay.relay.operation.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.net.ssl.SSLHandshakeException;

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
    private final Duration connectTimeout;
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
        this.connectTimeout = connectTimeout;
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
        } catch (HttpConnectTimeoutException timeout) {
            return HttpCall.connectTimeout(
                    "connect timeout after " + connectTimeout.toMillis() + "ms"
            );
        } catch (HttpTimeoutException timeout) {
            return HttpCall.requestTimeout(
                    "request timeout after " + requestTimeout.toMillis() + "ms"
            );
        } catch (ConnectException | UnknownHostException | NoRouteToHostException
                | SSLHandshakeException networkError) {
            // The request never left the machine: refused, DNS, no route,
            // or TLS handshake failed.
            return HttpCall.notSent(describe(networkError));
        } catch (IOException networkError) {
            // The request may have been written before the connection broke.
            return HttpCall.ambiguous(describe(networkError));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return HttpCall.ambiguous("interrupted while waiting for response");
        }
    }

    private static String describe(Exception exception) {
        return exception.getClass().getSimpleName()
                + ": "
                + exception.getMessage();
    }

    private HttpRequest buildRequest(Operation operation) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(operation.getTargetUrl())
                )
                .timeout(requestTimeout)
                .method(
                        operation.getHttpMethod(),
                        bodyPublisher(operation.getRequestBody())
                )
                .header("Idempotency-Key", operation.getId());

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
