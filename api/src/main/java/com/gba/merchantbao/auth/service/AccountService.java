package com.gba.merchantbao.auth.service;

import com.gba.merchantbao.auth.entity.ConsumerAccountRow;
import com.gba.merchantbao.auth.entity.StaffAccountRow;
import com.gba.merchantbao.auth.entity.Tenant;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.mapper.ConsumerAccountMapper;
import com.gba.merchantbao.auth.mapper.StaffAccountMapper;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.util.AuthInput;

import com.gba.merchantbao.auth.service.TenantResolver;
import com.gba.merchantbao.common.error.CommonErrorCode;
import com.gba.merchantbao.common.id.IdGenerator;
import org.springframework.dao.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;

@Service
public class AccountService {
    private final TenantResolver tenants; private final ConsumerAccountMapper consumers; private final StaffAccountMapper staff;
    private final SmsChallengeService sms; private final AuthRateLimiter rates; private final SessionService sessions;
    private final IdGenerator ids; private final Clock clock; private final TransactionTemplate tx;
    private final BCryptPasswordEncoder bcrypt=new BCryptPasswordEncoder(12);
    private final String dummy=bcrypt.encode("DummyOnly-NotAnAccount-2026!");
    public AccountService(TenantResolver tenants,ConsumerAccountMapper consumers,StaffAccountMapper staff,SmsChallengeService sms,AuthRateLimiter rates,SessionService sessions,IdGenerator ids,Clock clock,PlatformTransactionManager transactions) {
        this.tenants=tenants;this.consumers=consumers;this.staff=staff;this.sms=sms;this.rates=rates;this.sessions=sessions;this.ids=ids;this.clock=clock;this.tx=new TransactionTemplate(transactions);
    }
    public Tenant store(String slug) {
        try{return tenants.resolveActive(AuthInput.storeSlug(slug)).orElseThrow(()->new AuthError(404,CommonErrorCode.RESOURCE_NOT_FOUND));}
        catch(DataAccessException e){throw AuthError.dependency();}
    }
    public SessionService.Tokens consumerLogin(String slug,String phone,String code,String ip) {
        rates.login(ip); var tenant=store(slug); String normalized=AuthInput.phone(phone);
        sms.consume(tenant.id(),normalized,code); // deliberately outside the database transaction: never restored on failure
        ConsumerAccountRow account;
        try {
            try {account=tx.execute(status->{
                var found=consumers.findByPhone(tenant.id(),normalized); if(found!=null)return found;
                var n=clock.instant();var created=new ConsumerAccountRow(ids.nextId(),tenant.id(),normalized,"消费者","ACTIVE",n,n,0);
                consumers.insert(created);return created;
            });} catch(DuplicateKeyException race){account=consumers.findByPhone(tenant.id(),normalized);}
        } catch(DataAccessException e){throw AuthError.dependency();}
        if(account==null||!"ACTIVE".equals(account.status()))throw AuthError.credentials();
        // Account transaction has committed before any session is returned or created.
        return sessions.login(new AuthPrincipal(account.id(),tenant.id(),AccountRole.CONSUMER));
    }
    public SessionService.Tokens staffLogin(String merchantCode,String identifier,String password,String ip) {
        rates.login(ip); String code=AuthInput.merchantCode(merchantCode);var id=AuthInput.identifier(identifier);AuthInput.password(password);
        try {
            var tenant=tenants.resolveActive(code).orElse(null);
            var row=tenant==null?null:(id.phone()?staff.findByPhone(tenant.id(),id.value()):staff.findByUsername(tenant.id(),id.value()));
            String key=rates.passwordKey(tenant==null?0:tenant.id(),row==null?null:row.id(),id.value());
            rates.passwordAllowed(key);
            boolean matches=bcrypt.matches(password,row==null?dummy:row.passwordHash());
            if(row==null||!matches||!"ACTIVE".equals(row.status())) {rates.passwordFailed(key);throw AuthError.credentials();}
            rates.passwordSucceeded(key);return sessions.login(new AuthPrincipal(row.id(),tenant.id(),AccountRole.MERCHANT_STAFF));
        } catch(DataAccessException e){throw AuthError.dependency();}
    }
    public AccountView createStaff(Actor actor,String username,String phone,String displayName,String password) {
        requireRole(actor,AccountRole.MERCHANT_STAFF);username=AuthInput.username(username);phone=AuthInput.phone(phone);AuthInput.password(password);
        if(displayName==null||displayName.isBlank()||displayName.length()>64)throw new IllegalArgumentException("Invalid display name");
        var n=clock.instant();var p=actor.principal();
        var row=new StaffAccountRow(ids.nextId(),p.merchantId(),username,phone,displayName.trim(),bcrypt.encode(password),"ACTIVE",n,n,p.accountId(),0);
        try {tx.executeWithoutResult(status->staff.insert(row));}
        catch(DuplicateKeyException e){throw new AuthError(409,CommonErrorCode.CONFLICT);}
        catch(DataAccessException e){throw AuthError.dependency();}
        return view(row,storeByActor(actor).merchantCode());
    }
    private Tenant storeByActor(Actor actor){return tenants.resolveActiveById(actor.principal().merchantId()).orElseThrow(AuthError::unauthorized);}
    public AccountView staffResource(Actor actor,long id) {
        requireRole(actor,AccountRole.MERCHANT_STAFF);
        try {var r=staff.findById(actor.principal().merchantId(),id);if(r==null)throw missing();return view(r,storeByActor(actor).merchantCode());}
        catch(DataAccessException e){throw AuthError.dependency();}
    }
    public AccountView consumerResource(Actor actor,long id) {
        requireRole(actor,AccountRole.CONSUMER);
        try {var r=consumers.findOwnedById(actor.principal().merchantId(),id,actor.principal().accountId());if(r==null)throw missing();return new AccountView(new com.gba.merchantbao.common.api.ApiId(r.id()),storeByActor(actor).merchantCode(),null,r.displayName(),com.gba.merchantbao.common.privacy.PhoneMasker.maskPhone(r.phone()),AccountRole.CONSUMER,r.status());}
        catch(DataAccessException e){throw AuthError.dependency();}
    }
    public static void requireRole(Actor actor,AccountRole role){if(actor.principal().role()!=role)throw new AuthError(403,CommonErrorCode.FORBIDDEN);}
    private AuthError missing(){return new AuthError(404,CommonErrorCode.RESOURCE_NOT_FOUND);}
    private AccountView view(StaffAccountRow row,String code){return new AccountView(new com.gba.merchantbao.common.api.ApiId(row.id()),code,row.username(),row.displayName(),com.gba.merchantbao.common.privacy.PhoneMasker.maskPhone(row.phone()),AccountRole.MERCHANT_STAFF,row.status());}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record AccountView(com.gba.merchantbao.common.api.ApiId accountId,String merchantCode,String username,String displayName,String phoneMasked,AccountRole role,String status) { }
}
