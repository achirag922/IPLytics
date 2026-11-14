package com.iplytics.common.exception;

/** The requested resource does not exist (404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException("%s %s not found".formatted(resource, id));
    }
}
