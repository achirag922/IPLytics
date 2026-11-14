package com.iplytics.common.exception;

/**
 * Well-formed but semantically invalid request (422), e.g. comparing a team with itself or filtering by a team id
 * that does not exist.
 */
public class UnprocessableRequestException extends RuntimeException {

    public UnprocessableRequestException(String message) {
        super(message);
    }
}
