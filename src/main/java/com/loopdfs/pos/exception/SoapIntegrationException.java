package com.loopdfs.pos.exception;

public class SoapIntegrationException extends RuntimeException {
    public SoapIntegrationException(String message) {
        super(message);
    }
    public SoapIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
