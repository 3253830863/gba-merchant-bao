package com.gba.merchantbao.auth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.auth.config.AuthLimits;
import com.gba.merchantbao.auth.domain.*;
import com.gba.merchantbao.auth.infrastructure.*;
import com.gba.merchantbao.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SessionService {
    private final AuthRedis redis;
    private final AccessTokenService access;
    private final AccountDirectory accounts;
    private final RefreshCipher cipher;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuthLimits limits;
    public SessionService(AuthRedis redis,AccessTokenService access,AccountDirectory accounts,RefreshCipher cipher,ObjectMapper json,Clock clock,AuthLimits limits) {
        this.redis=redis;this.access=access;this.accounts=accounts;this.cipher=cipher;this.json=json;this.clock=clock;this.limits=limits;
    }
    public static long idle(AccountRole role) {return role==AccountRole.CONSUMER?604800:86400;}
    public static long absolute(AccountRole role) {return role==AccountRole.CONSUMER?2592000:604800;}
    private Instant now(){return clock.instant().truncatedTo(ChronoUnit.SECONDS);}
    private String identity(AuthPrincipal p){return p.merchantId()+":"+p.role()+":"+p.accountId();}
    private String active(AuthPrincipal p,Instant idleAt){return encode(Map.of("identity",identity(p),"idleAt",idleAt.getEpochSecond()));}
    private String session(String sid){return redis.key("session:"+sid);}
    private String control(String sid){return redis.key("control:"+sid);}
    private String index(String hash){return redis.key("refresh:"+hash);}
    public Tokens login(AuthPrincipal p) {
        var view=accounts.active(p); Instant n=now(),absoluteAt=n.plusSeconds(absolute(p.role())),idleAt=n.plusSeconds(idle(p.role()));
        String sid=AuthCrypto.randomToken(),refresh=AuthCrypto.randomToken(),hash=AuthCrypto.hash(refresh);
        var result=tokens(p,sid,n,absoluteAt,refresh,view);
        if(!"1".equals(redis.run("session-create",List.of(session(sid),control(sid),index(hash)),active(p,idleAt),""+idleAt.getEpochSecond(),identity(p),""+p.accountId(),""+p.merchantId(),p.role().name(),""+n.getEpochSecond(),""+absoluteAt.getEpochSecond(),hash,sid))) throw AuthError.dependency();
        return result;
    }
    public Actor authenticate(String token) {
        var verified=access.verify(token,now()); var p=verified.principal();
        if(!"1".equals(redis.run("session-check",List.of(session(verified.sessionId()),control(verified.sessionId())),identity(p),""+now().getEpochSecond(),"CHECK",""+idle(p.role()),redis.key("refresh:")))) throw AuthError.unauthorized();
        accounts.active(p); return new Actor(p,verified.sessionId());
    }
    /** Called only after a successful foreground private operation, never me or background probes. */
    public void touch(Actor actor) {
        var p=actor.principal();
        redis.run("session-check",List.of(session(actor.sessionId()),control(actor.sessionId())),identity(p),""+now().getEpochSecond(),"TOUCH",""+idle(p.role()),redis.key("refresh:"));
    }
    public Tokens refresh(String token,String requestId) {
        validateRefresh(token);
        try {if(!UUID.fromString(requestId).toString().equals(requestId))throw new IllegalArgumentException();}
        catch(RuntimeException e){throw new IllegalArgumentException("Invalid refresh request ID");}
        String oldHash=AuthCrypto.hash(token); var idx=redis.hash(index(oldHash));
        String sid=(String)idx.get("sid"); if(sid==null) throw AuthError.unauthorized();
        var c=redis.hash(control(sid)); var p=principal(c); Instant n=now(),absoluteAt=Instant.ofEpochSecond(number(c,"absoluteAt"));
        if(!"ACTIVE".equals(c.get("state")) || !absoluteAt.isAfter(n))throw AuthError.unauthorized();
        var view=accounts.active(p);
        long next=number(idx,"generation")+1;
        String refresh=AuthCrypto.randomToken(),hash=AuthCrypto.hash(refresh),retry=redis.key("refresh-retry:"+sid+":"+requestId);
        var proposed=tokens(p,sid,n,absoluteAt,refresh,view);
        String aad=sid+":"+next+":"+requestId+":"+absoluteAt.getEpochSecond();
        String encrypted=cipher.encrypt(p.role(),aad,encode(proposed));
        Instant idleAt=n.plusSeconds(idle(p.role())); if(idleAt.isAfter(absoluteAt)) idleAt=absoluteAt;
        String result=redis.run("session-rotate",List.of(session(sid),control(sid),index(oldHash),index(hash),retry),""+n.getEpochSecond(),identity(p),sid,requestId,oldHash,hash,active(p,idleAt),""+idleAt.getEpochSecond(),encrypted,""+limits.getRefreshRetrySeconds(),redis.key("refresh:"));
        if(result.equals("OK"))return proposed;
        if(result.startsWith("RETRY:")) {
            try{return json.readValue(cipher.decrypt(p.role(),aad,result.substring(6)),Tokens.class);}
            catch(com.fasterxml.jackson.core.JsonProcessingException e){throw AuthError.unauthorized();}
        }
        throw AuthError.unauthorized();
    }
    /** A signed unexpired access credential can revoke even when active/account state has disappeared. */
    public Actor logout(String accessToken,String refreshToken) {
        String accessSid=null,refreshSid=null;
        try {accessSid=access.verify(accessToken,now()).sessionId();}catch(InvalidAccessTokenException ignored) { }
        if(refreshToken!=null&&!refreshToken.isBlank()) {
            validateRefresh(refreshToken); refreshSid=(String)redis.hash(index(AuthCrypto.hash(refreshToken))).get("sid");
        }
        if(accessSid!=null&&refreshSid!=null&&!accessSid.equals(refreshSid))throw new AuthError(400,CommonErrorCode.INVALID_ARGUMENT);
        String sid=accessSid!=null?accessSid:refreshSid;
        if(sid==null)throw AuthError.unauthorized();
        Actor actor=new Actor(principal(redis.hash(control(sid))),sid);
        if(!"1".equals(redis.run("session-revoke",List.of(session(sid),control(sid)),""+now().getEpochSecond(),redis.key("refresh:"))))throw AuthError.unauthorized();
        return actor;
    }
    private void validateRefresh(String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw AuthError.unauthorized();}
    private AuthPrincipal principal(Map<Object,Object> c) {
        try{return new AuthPrincipal(number(c,"accountId"),number(c,"merchantId"),AccountRole.valueOf((String)c.get("role")));}
        catch(RuntimeException e){throw AuthError.unauthorized();}
    }
    private long number(Map<Object,Object> map,String name){try{return Long.parseLong((String)map.get(name));}catch(RuntimeException e){throw AuthError.unauthorized();}}
    private Tokens tokens(AuthPrincipal p,String sid,Instant n,Instant absoluteAt,String refresh,AccountDirectory.PrincipalView view) {
        var issued=access.issue(p,sid,n,absoluteAt);
        return new Tokens(issued.token(),refresh,"Bearer",Duration.between(n,issued.expiresAt()).getSeconds(),issued.expiresAt(),absoluteAt,view);
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw AuthError.dependency();}}
    public record Tokens(String accessToken,String refreshToken,String tokenType,long expiresIn,Instant expiresAt,Instant refreshExpiresAt,AccountDirectory.PrincipalView principal) {
        @Override public String toString(){return "Tokens[REDACTED]";}
    }
}
