package com.relay.relay.execution;

/**
 * The result of one HTTP call, before it is mapped to attempt/operation states.
 *
 * A null failure means a response arrived. Otherwise Failure says whether the
 * request was definitely not sent or may have reached the destination. That
 * distinction decides FAILED vs UNKNOWN.
 */
public record HttpCall(
        Integer statusCode,
        Failure failure,
        String error
) {

    public enum Failure {
        CONNECT_TIMEOUT,
        REQUEST_TIMEOUT,
        NOT_SENT,
        AMBIGUOUS
    }

    public static HttpCall response(int statusCode) {
        return new HttpCall(statusCode, null, null);
    }

    public static HttpCall connectTimeout(String error) {
        return new HttpCall(null, Failure.CONNECT_TIMEOUT, error);
    }

    public static HttpCall requestTimeout(String error) {
        return new HttpCall(null, Failure.REQUEST_TIMEOUT, error);
    }

    public static HttpCall notSent(String error) {
        return new HttpCall(null, Failure.NOT_SENT, error);
    }

    public static HttpCall ambiguous(String error) {
        return new HttpCall(null, Failure.AMBIGUOUS, error);
    }
}
