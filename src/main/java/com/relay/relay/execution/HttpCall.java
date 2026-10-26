package com.relay.relay.execution;

/**
 * The result of one HTTP call, before it is mapped to attempt/operation states.
 */
public record HttpCall(
        Integer statusCode,
        boolean timedOut,
        String error
) {

    public static HttpCall response(int statusCode) {
        return new HttpCall(statusCode, false, null);
    }

    public static HttpCall timeout(String error) {
        return new HttpCall(null, true, error);
    }

    public static HttpCall failure(String error) {
        return new HttpCall(null, false, error);
    }
}
