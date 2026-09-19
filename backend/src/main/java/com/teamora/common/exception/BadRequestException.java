package com.teamora.common.exception;

/** Thrown for invalid business operations → maps to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
