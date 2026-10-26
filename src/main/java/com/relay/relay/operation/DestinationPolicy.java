package com.relay.relay.operation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single place that decides whether a destination is allowed.
 *
 * This is a basic SSRF guard for the demo: the relay only calls hosts the
 * operator listed. Real deployments also need DNS/IP pinning because a
 * hostname can resolve to an internal address after this check.
 */
@Component
public class DestinationPolicy {

    private static final int MAX_BODY_BYTES = 256 * 1024;

    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final Set<String> allowedHosts;

    public DestinationPolicy(
            @Value("${relay.destination.allowed-hosts:localhost,127.0.0.1}") String allowedHosts
    ) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .map(host -> host.toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @throws IllegalArgumentException with a caller-readable message
     *                                  when the destination is not allowed.
     */
    public URI validate(String targetUrl, String httpMethod, String requestBody) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("targetUrl is required");
        }

        URI uri;
        try {
            uri = URI.create(targetUrl);
        } catch (IllegalArgumentException invalidUri) {
            throw new IllegalArgumentException("targetUrl is not a valid URI");
        }

        if (uri.getScheme() == null
                || !(uri.getScheme().equalsIgnoreCase("http")
                || uri.getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("targetUrl must use http or https");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("targetUrl must contain a host");
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("targetUrl must not contain user information");
        }

        if (!allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "targetUrl host is not allowed: " + uri.getHost()
            );
        }

        if (httpMethod == null
                || !ALLOWED_METHODS.contains(httpMethod.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "httpMethod must be one of " + ALLOWED_METHODS
            );
        }

        if (requestBody != null
                && requestBody.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "requestBody must be at most 256 KB"
            );
        }

        return uri;
    }
}
