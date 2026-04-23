package com.workhub.tenant;

import com.workhub.exception.TenantContextMissingException;

public class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextMissingException();
        }
        currentTenant.set(tenantId);
    }

    public static String getTenantId() {
        String tenantId = currentTenant.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextMissingException();
        }
        return tenantId;
    }

    public static void clear() {
        currentTenant.remove();
    }
}