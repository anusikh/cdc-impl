package com.anusikh.cdcink.service.exception;

public class RetryableCdcException extends RuntimeException {

    public RetryableCdcException(String message, Throwable cause) {
        super(message, cause);
    }
}
