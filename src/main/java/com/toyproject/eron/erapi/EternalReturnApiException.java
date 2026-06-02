package com.toyproject.eron.erapi;

import org.springframework.http.HttpStatus;

public class EternalReturnApiException extends RuntimeException {

    private final HttpStatus status;

    public EternalReturnApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
