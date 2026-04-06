package com.anusikh.cdcink.service.exception;

public class NonRetryableCdcException extends RuntimeException {

    public NonRetryableCdcException(String message) {
        super(message);
    }
}
