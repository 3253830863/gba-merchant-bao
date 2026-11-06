package com.gba.merchantbao.auth.api;

import com.gba.merchantbao.auth.application.*;
import com.gba.merchantbao.auth.domain.*;
import com.gba.merchantbao.auth.infrastructure.AuthSecurityFilter;
import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController @RequestMapping("/api/v1") @Tag(name="auth-v1",description="Stateless auth; responses use Cache-Control: no-store. Errors: 400/10001 or 11002, 401/10002 or 11001, 403/10003, 404/10004, 409/10009, 429/11003 with Retry-After, 503/11501 or 11502. IDs are strings and times are UTC.")
public class AuthController {
    private final AccountService accounts;private final SmsChallengeService sms;private final AccountDirectory directory;private final SessionService sessions;
    public AuthController(AccountService accounts,SmsChallengeService sms,AccountDirectory directory,SessionService sessions){this.accounts=accounts;this.sms=sms;this.directory=directory;this.sessions=sessions;}
    @PostMapping("/stores/{storeSlug}/auth/sms-codes") @Operation(operationId="sendLoginSms",summary="发送 LOGIN 验证码",description="仅 dev mock 且显式打开时返回 debugCode；300 秒有效。生产短信 disabled 返回 503/11502。")
    public ApiResponse<SmsChallengeService.SmsResult> send(@PathVariable String storeSlug,@Valid @RequestBody AuthRequests.Sms body,HttpServletRequest r){return ok(sms.send(accounts.store(storeSlug).id(),AuthInput.phone(body.phone()),r.getRemoteAddr()),r);}
    @PostMapping("/stores/{storeSlug}/auth/consumer/login") @Operation(operationId="consumerLogin",summary="消费者短信登录，首次自动建号")
    public ApiResponse<SessionService.Tokens> consumer(@PathVariable String storeSlug,@Valid @RequestBody AuthRequests.ConsumerLogin body,HttpServletRequest r){return ok(accounts.consumerLogin(storeSlug,body.phone(),body.code(),r.getRemoteAddr()),r);}
    @PostMapping("/auth/staff/login") @Operation(operationId="staffLogin",summary="员工密码登录")
    public ApiResponse<SessionService.Tokens> staff(@Valid @RequestBody AuthRequests.StaffLogin body,HttpServletRequest r){return ok(accounts.staffLogin(body.merchantCode(),body.identifier(),body.password(),r.getRemoteAddr()),r);}
    @PostMapping("/auth/refresh") @Operation(operationId="refreshAuth",summary="轮换 refresh",description="凭 refresh body 验证；Authorization 即使无效也不阻断。requestId 为 UUID，同次重试必须复用。",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(schema=@Schema(implementation=AuthRequests.Refresh.class))))
    public ApiResponse<SessionService.Tokens> refresh(HttpServletRequest r){return ok((SessionService.Tokens)r.getAttribute(AuthSecurityFilter.RESULT),r);}
    @PostMapping("/auth/logout") @Operation(operationId="logoutAuth",summary="撤销单个会话",description="有效未过期 Bearer 或 body 中已知 refresh；两者有效时须同 sid。重复退出 200。",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(content=@Content(schema=@Schema(implementation=AuthRequests.Logout.class))))
    public ApiResponse<Void> logout(HttpServletRequest r){return ok(null,r);}
    @GetMapping("/auth/me") @SecurityRequirement(name="bearerAuth") @Operation(operationId="authMe",summary="当前主体，不推进 idle")
    public ApiResponse<AccountDirectory.PrincipalView> me(@AuthenticationPrincipal Actor actor,HttpServletRequest r){return ok(directory.active(actor.principal()),r);}
    @PostMapping("/staff-accounts") @SecurityRequirement(name="bearerAuth") @Operation(operationId="createStaffAccount",summary="员工受控开通")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="Created; Location: /api/v1/staff-accounts/{accountId}")
    public ResponseEntity<ApiResponse<AccountService.AccountView>> create(@AuthenticationPrincipal Actor actor,@Valid @RequestBody AuthRequests.StaffCreate body,HttpServletRequest r){var result=accounts.createStaff(actor,body.username(),body.phone(),body.displayName(),body.password());sessions.touch(actor);return ResponseEntity.created(URI.create("/api/v1/staff-accounts/"+result.accountId().asString())).body(ok(result,r));}
    @GetMapping("/staff-accounts/{accountId}") @SecurityRequirement(name="bearerAuth") @Operation(operationId="getStaffAccount",summary="同租户员工")
    public ApiResponse<AccountService.AccountView> staffResource(@AuthenticationPrincipal Actor actor,@PathVariable long accountId,HttpServletRequest r){var result=accounts.staffResource(actor,accountId);sessions.touch(actor);return ok(result,r);}
    @GetMapping("/consumer-accounts/{accountId}") @SecurityRequirement(name="bearerAuth") @Operation(operationId="getConsumerAccount",summary="同租户消费者本人")
    public ApiResponse<AccountService.AccountView> consumerResource(@AuthenticationPrincipal Actor actor,@PathVariable long accountId,HttpServletRequest r){var result=accounts.consumerResource(actor,accountId);sessions.touch(actor);return ok(result,r);}
    private <T>ApiResponse<T> ok(T value,HttpServletRequest r){
        if(value instanceof SessionService.Tokens tokens){var p=tokens.principal();r.setAttribute(com.gba.merchantbao.auth.infrastructure.AuthAudit.ACTOR,
            new Actor(new AuthPrincipal(p.accountId().value(),p.merchantId().value(),p.role()),""));}
        return ApiResponse.success(value,(String)r.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
