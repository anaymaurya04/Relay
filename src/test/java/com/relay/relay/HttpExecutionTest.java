package com.relay.relay;

import com.relay.relay.execution.HttpOperationExecutor;
import com.relay.relay.operation.Operation;
import com.relay.relay.worker.OperationProcessor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real executor against a real local HTTP server.
 *
 * The scheduled worker is disabled; the test calls OperationProcessor
 * directly so it does not depend on timing.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "relay.worker.enabled=false",
                "relay.http.request-timeout=500ms",
                "relay.http.connect-timeout=300ms"
        }
)
class HttpExecutionTest {

    @Value("${local.server.port}")
    private int appPort;

    @Autowired
    private OperationProcessor operationProcessor;

    @Autowired
    private HttpOperationExecutor httpOperationExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static HttpServer destination;
    private static ExecutorService destinationExecutor;
    private static String destinationUrl;
    private static final ConcurrentLinkedQueue<String> receivedIdempotencyKeys =
            new ConcurrentLinkedQueue<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final List<String> createdCallers = new ArrayList<>();

    @BeforeAll
    static void startDestinationServer() throws IOException {
        destination = HttpServer.create(
                new InetSocketAddress("localhost", 0),
                0
        );
        destinationExecutor = Executors.newFixedThreadPool(4);
        destination.setExecutor(destinationExecutor);

        destination.createContext("/ok", exchange -> {
            receivedIdempotencyKeys.add(
                    exchange.getRequestHeaders().getFirst("Idempotency-Key")
            );
            respond(exchange, 200);
        });
        destination.createContext("/bad", exchange -> respond(exchange, 400));
        destination.createContext("/fail", exchange -> respond(exchange, 500));

        destination.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok");
            respond(exchange, 302);
        });

        destination.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200);
        });

        destination.start();
        destinationUrl = "http://localhost:"
                + destination.getAddress().getPort();
    }

    @AfterAll
    static void stopDestinationServer() {
        destinationExecutor.shutdownNow();
        destination.stop(0);
    }

    @AfterEach
    void deleteCreatedRows() {
        for (String caller : createdCallers) {
            jdbcTemplate.update(
                    """
                    DELETE FROM attempts
                    WHERE operation_id IN (
                        SELECT id FROM operations WHERE caller_id = ?
                    )
                    """,
                    caller
            );
            jdbcTemplate.update(
                    "DELETE FROM operations WHERE caller_id = ?",
                    caller
            );
        }
        createdCallers.clear();
    }

    @Test
    void successfulCallMarksAttemptSucceededAndOperationSuccess() throws Exception {
        receivedIdempotencyKeys.clear();
        String id = createOperation(destinationUrl + "/ok");

        operationProcessor.processAll();

        assertEquals("SUCCESS", operationState(id));

        Map<String, Object> attempt = onlyAttempt(id);
        assertEquals("SUCCEEDED", attempt.get("state"));
        assertEquals(200, responseCode(attempt));
        assertNull(attempt.get("error"));

        assertEquals(
                List.of(id),
                new ArrayList<>(receivedIdempotencyKeys),
                "the destination must receive the operation id as Idempotency-Key"
        );

        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM operations WHERE id = ?",
                Long.class,
                id
        );
        assertTrue(version != null && version >= 2, "version=" + version);

        String attemptsBody = getBody("/operations/" + id + "/attempts");
        assertTrue(attemptsBody.contains("\"state\":\"SUCCEEDED\""));
    }

    @Test
    void clientErrorMarksOperationFailedWithoutRetry() throws Exception {
        String id = createOperation(destinationUrl + "/bad");

        operationProcessor.processAll();

        assertEquals("FAILED", operationState(id));

        Map<String, Object> attempt = onlyAttempt(id);
        assertEquals("FAILED", attempt.get("state"));
        assertEquals(400, responseCode(attempt));
    }

    @Test
    void serverErrorMarksAttemptFailedAndOperationRetryWaiting() throws Exception {
        String id = createOperation(destinationUrl + "/fail");

        operationProcessor.processAll();

        assertEquals("RETRY_WAITING", operationState(id));

        Map<String, Object> attempt = onlyAttempt(id);
        assertEquals("FAILED", attempt.get("state"));
        assertEquals(500, responseCode(attempt));
    }

    @Test
    void timeoutMarksAttemptUnknownAndOperationRetryWaiting() throws Exception {
        String id = createOperation(destinationUrl + "/slow");

        operationProcessor.processAll();

        assertEquals("RETRY_WAITING", operationState(id));

        Map<String, Object> attempt = onlyAttempt(id);
        assertEquals("UNKNOWN", attempt.get("state"));
        assertNull(attempt.get("response_code"));
        assertTrue(((String) attempt.get("error")).contains("timeout"));
    }

    @Test
    void connectionRefusedMarksAttemptFailedAndOperationRetryWaiting() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        String id = createOperation(
                "http://localhost:" + closedPort + "/ok"
        );

        operationProcessor.processAll();

        assertEquals("RETRY_WAITING", operationState(id));

        Map<String, Object> attempt = onlyAttempt(id);
        assertEquals("FAILED", attempt.get("state"));
        assertNull(attempt.get("response_code"));
    }

    @Test
    void connectionResetAfterRequestIsAmbiguousAndMarkedUnknown() throws Exception {
        try (ServerSocket resetServer = new ServerSocket(0)) {
            Thread resetThread = new Thread(() -> {
                while (!resetServer.isClosed()) {
                    try (Socket socket = resetServer.accept()) {
                        // SO_LINGER 0 makes close send RST instead of FIN.
                        socket.setSoLinger(true, 0);
                    } catch (IOException closed) {
                        return;
                    }
                }
            });
            resetThread.setDaemon(true);
            resetThread.start();

            String id = createOperation(
                    "http://localhost:" + resetServer.getLocalPort() + "/reset"
            );

            operationProcessor.processAll();

            assertEquals("RETRY_WAITING", operationState(id));

            Map<String, Object> attempt = onlyAttempt(id);
            assertEquals("UNKNOWN", attempt.get("state"));
            assertNull(attempt.get("response_code"));
        }
    }

    @Test
    void repeatedExecutionSendsSameIdempotencyKey() {
        Operation operation = new Operation(
                UUID.randomUUID().toString(),
                "direct-" + UUID.randomUUID(),
                "direct-key",
                destinationUrl + "/ok",
                "POST",
                null
        );

        receivedIdempotencyKeys.clear();
        httpOperationExecutor.execute(operation);
        httpOperationExecutor.execute(operation);

        assertEquals(
                List.of(operation.getId(), operation.getId()),
                new ArrayList<>(receivedIdempotencyKeys)
        );
    }

    @Test
    void claimsOperationsInCreatedAtOrder() throws Exception {
        String later = createOperation(destinationUrl + "/ok");
        String earlier = createOperation(destinationUrl + "/ok");

        jdbcTemplate.update(
                "UPDATE operations SET created_at = now() - interval '1 hour' WHERE id = ?",
                earlier
        );
        jdbcTemplate.update(
                "UPDATE operations SET created_at = now() WHERE id = ?",
                later
        );

        receivedIdempotencyKeys.clear();
        operationProcessor.processAll();

        assertEquals(
                List.of(earlier, later),
                new ArrayList<>(receivedIdempotencyKeys),
                "claims must follow created_at, not the random id"
        );
    }

    @Test
    void redirectIsNotFollowedAndFailsPermanently() throws Exception {
        String id = createOperation(destinationUrl + "/redirect");

        operationProcessor.processAll();

        assertEquals("FAILED", operationState(id));

        Map<String, Object> attempt = onlyAttempt(id);
        assertEquals("FAILED", attempt.get("state"));
        assertEquals(302, responseCode(attempt));
    }

    @Test
    void rejectsNonHttpScheme() throws Exception {
        String callerId = registerCaller();

        HttpResponse<String> response = postOperation(
                callerId,
                "ftp://localhost/file",
                "POST"
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("http"));
    }

    @Test
    void rejectsHostOutsideAllowlist() throws Exception {
        String callerId = registerCaller();

        HttpResponse<String> response = postOperation(
                callerId,
                "http://example.com/ok",
                "POST"
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("not allowed"));
    }

    @Test
    void rejectsInvalidHttpMethod() throws Exception {
        String callerId = registerCaller();

        HttpResponse<String> response = postOperation(
                callerId,
                destinationUrl + "/ok",
                "TRACE"
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("httpMethod"));
    }

    private String createOperation(String targetUrl) throws Exception {
        HttpResponse<String> response = postOperation(
                registerCaller(),
                targetUrl,
                "POST"
        );

        assertEquals(200, response.statusCode(), response.body());

        String id = jsonField(response.body(), "id");
        assertTrue(id != null && !id.isBlank(), response.body());
        return id;
    }

    private String registerCaller() {
        String callerId = "http-test-" + UUID.randomUUID();
        createdCallers.add(callerId);
        return callerId;
    }

    private HttpResponse<String> postOperation(
            String callerId,
            String targetUrl,
            String httpMethod
    ) throws Exception {
        String requestBody = "{\"targetUrl\":\""
                + targetUrl
                + "\",\"httpMethod\":\""
                + httpMethod
                + "\"}";

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + appPort + "/operations")
                )
                .timeout(Duration.ofSeconds(10))
                .header("Caller-Id", callerId)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String operationState(String operationId) throws Exception {
        return jsonField(getBody("/operations/" + operationId), "state");
    }

    private String getBody(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + appPort + path)
                )
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    private Map<String, Object> onlyAttempt(String operationId) {
        List<Map<String, Object>> attempts = jdbcTemplate.queryForList(
                """
                SELECT attempt_number, state, response_code, error
                FROM attempts
                WHERE operation_id = ?
                ORDER BY attempt_number
                """,
                operationId
        );

        assertEquals(1, attempts.size(), attempts.toString());
        return attempts.get(0);
    }

    private static int responseCode(Map<String, Object> attempt) {
        return ((Number) attempt.get("response_code")).intValue();
    }

    private static String jsonField(String body, String field) {
        Matcher matcher = Pattern.compile(
                        "\"" + field + "\"\\s*:\\s*\"([^\"]*)\""
                )
                .matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void respond(HttpExchange exchange, int status)
            throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
