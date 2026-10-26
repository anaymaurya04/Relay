package com.relay.relay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that concurrent POSTs with the same caller/key create one operation.
 * The worker is disabled so only the creation path is under test.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "relay.worker.enabled=false"
)
class IdempotencyConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 8;

    private static final String REQUEST_BODY = """
            {"targetUrl":"http://localhost:1/ok","httpMethod":"POST"}
            """;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String callerId;

    @AfterEach
    void cleanUp() {
        if (callerId == null) {
            return;
        }

        jdbcTemplate.update(
                """
                DELETE FROM attempts
                WHERE operation_id IN (
                    SELECT id FROM operations WHERE caller_id = ?
                )
                """,
                callerId
        );
        jdbcTemplate.update(
                "DELETE FROM operations WHERE caller_id = ?",
                callerId
        );
    }

    @Test
    void concurrentDuplicateRequestsCreateOneOperation() throws Exception {
        callerId = "idem-race-" + UUID.randomUUID();
        String idempotencyKey = "same-key";

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        ExecutorService executor =
                Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_REQUESTS);

        try {
            List<Future<Result>> futures = new ArrayList<>();

            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(
                                    "http://localhost:" + port + "/operations"
                            ))
                            .timeout(Duration.ofSeconds(10))
                            .header("Caller-Id", callerId)
                            .header("Idempotency-Key", idempotencyKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(REQUEST_BODY))
                            .build();

                    HttpResponse<String> response = httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

                    return new Result(
                            response.statusCode(),
                            extractId(response.body())
                    );
                }));
            }

            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertEquals(CONCURRENT_REQUESTS, results.size());
            assertTrue(
                    results.stream().allMatch(result -> result.status() == 200),
                    () -> "Expected all 200, got: " + results
            );

            Set<String> ids = new HashSet<>();
            for (Result result : results) {
                assertNotNull(
                        result.id(),
                        "Response missing id: " + result
                );
                ids.add(result.id());
            }

            assertEquals(
                    1,
                    ids.size(),
                    "Expected one operation id, got: " + ids
            );

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM operations WHERE caller_id = ?",
                    Integer.class,
                    callerId
            );
            assertEquals(1, count);
        } finally {
            executor.shutdownNow();
        }
    }

    // shortcut: regex is enough for this two-field response; use a JSON parser when it grows.
    private static String extractId(String body) {
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private record Result(int status, String id) {
    }
}
