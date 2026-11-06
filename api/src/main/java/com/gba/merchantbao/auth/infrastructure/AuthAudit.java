package com.gba.merchantbao.auth.infrastructure;

import com.gba.merchantbao.auth.domain.Actor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Deliberately accepts no body, identifier, credential or phone. */
public final class AuthAudit {
    public static final String ACTOR=AuthAudit.class.getName()+".actor";
    private AuthAudit() { }
    public static void record(String event,Actor actor,int code,long durationMs) {
        String previous=MDC.get("module");
        MDC.put("module","auth");
        try {
        var log=LoggerFactory.getLogger("com.gba.merchantbao.auth.audit").atInfo()
            .addKeyValue("event",event)
            .addKeyValue("code",code).addKeyValue("durationMs",durationMs);
        if(actor!=null)log.addKeyValue("actor",Long.toString(actor.principal().accountId())).addKeyValue("merchant",Long.toString(actor.principal().merchantId()));
        log.log("auth_event");
        } finally {if(previous==null)MDC.remove("module");else MDC.put("module",previous);}
    }
}
