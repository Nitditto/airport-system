package com.nitditto.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 401 kèm {@code WWW-Authenticate: Bearer} theo RFC 6750. */
public class UnauthorizedException extends ResponseStatusException {

    public UnauthorizedException(final String reason) {
        super(HttpStatus.UNAUTHORIZED, reason);
    }

    @Override
    public HttpHeaders getHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return headers;
    }
}
