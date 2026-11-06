package com.gba.merchantbao.auth.service;

import com.gba.merchantbao.auth.entity.Tenant;
import com.gba.merchantbao.auth.mapper.AuthTenantMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public final class TenantResolver {
    private final AuthTenantMapper tenants;

    public TenantResolver(AuthTenantMapper tenants) {
        this.tenants = tenants;
    }

    /** Input must already be normalized. storeSlug currently uses the same canonical merchant code. */
    public Optional<Tenant> resolveActive(String normalizedMerchantCode) {
        if (normalizedMerchantCode == null || normalizedMerchantCode.isBlank()) return Optional.empty();
        return Optional.ofNullable(tenants.findActiveByCode(normalizedMerchantCode));
    }
    public Optional<Tenant> resolveActiveById(long merchantId) {
        return Optional.ofNullable(tenants.findActiveById(merchantId));
    }
}
