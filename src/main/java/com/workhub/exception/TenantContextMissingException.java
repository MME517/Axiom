package com.workhub.exception;

public class TenantContextMissingException extends RuntimeException {
    public TenantContextMissingException() {
        super("Tenant context is missing from the current request");
    }
}
