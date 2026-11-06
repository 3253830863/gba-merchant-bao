package com.gba.merchantbao.auth.service;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.mapper.AuthTenantMapper;
import com.gba.merchantbao.auth.mapper.ConsumerAccountMapper;
import com.gba.merchantbao.auth.mapper.StaffAccountMapper;
import com.gba.merchantbao.auth.security.AuthPrincipal;

import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.common.privacy.PhoneMasker;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

@Service
public class AccountDirectory {
    private final AuthTenantMapper tenants;
    private final ConsumerAccountMapper consumers;
    private final StaffAccountMapper staff;
    public AccountDirectory(AuthTenantMapper tenants, ConsumerAccountMapper consumers, StaffAccountMapper staff) {
        this.tenants=tenants; this.consumers=consumers; this.staff=staff;
    }
    public PrincipalView active(AuthPrincipal p) {
        try {
            var tenant=tenants.findActiveById(p.merchantId());
            if(tenant==null) throw AuthError.unauthorized();
            if(p.role()==AccountRole.CONSUMER) {
                var row=consumers.findById(p.merchantId(),p.accountId());
                if(row==null || !"ACTIVE".equals(row.status())) throw AuthError.unauthorized();
                return view(p,tenant.merchantCode(),row.displayName(),row.phone());
            }
            var row=staff.findById(p.merchantId(),p.accountId());
            if(row==null || !"ACTIVE".equals(row.status())) throw AuthError.unauthorized();
            return view(p,tenant.merchantCode(),row.displayName(),row.phone());
        } catch(DataAccessException e) { throw AuthError.dependency(); }
    }
    public static PrincipalView view(AuthPrincipal p,String code,String name,String phone) {
        return new PrincipalView(new ApiId(p.accountId()),new ApiId(p.merchantId()),code,p.role(),name,PhoneMasker.maskPhone(phone));
    }
    public record PrincipalView(ApiId accountId,ApiId merchantId,String merchantCode,AccountRole role,String displayName,String phoneMasked) { }
}
