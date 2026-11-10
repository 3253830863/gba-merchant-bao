package com.gba.merchantbao.chat.controller;

import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.AgentModel;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.chat.config.ChatStreamingConfiguration;
import com.gba.merchantbao.chat.dto.*;
import com.gba.merchantbao.chat.exception.ChatError;
import com.gba.merchantbao.chat.service.*;
import com.gba.merchantbao.chat.vo.*;
import com.gba.merchantbao.common.api.*;
import com.gba.merchantbao.common.error.CommonErrorCode;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatController {
    private final ChatService service; private final AgentProperties properties;
    private final ExecutorService writers;
    private final ScheduledExecutorService timer;
    public ChatController(ChatService service,AgentProperties properties,ObjectMapper json,@Qualifier("chatSseWriters") ExecutorService writers,
                          @Qualifier("chatSseHeartbeats") ScheduledExecutorService timer){
        this.service=service;this.properties=properties;this.writers=writers;this.timer=timer;
    }
    @PostMapping
    public ResponseEntity<ApiResponse<ChatSessionView>> create(java.security.Principal principal,@RequestBody(required=false) SessionCreateRequest body,
                                                               HttpServletRequest request) {
        if(body==null&&(request.getContentLengthLong()>0||request.getHeader(HttpHeaders.TRANSFER_ENCODING)!=null))throw ChatError.invalid();
        if(body!=null)body.validate();var row=service.create(actor(principal),body==null?null:body.title());var view=ChatSessionView.from(row);
        return ResponseEntity.created(java.net.URI.create("/api/v1/chat/sessions/"+view.sessionId().asString()))
                .cacheControl(CacheControl.noStore()).body(ApiResponse.success(view,requestId(request)));
    }
    @GetMapping
    public ApiResponse<PageView<ChatSessionView>> list(java.security.Principal principal,@RequestParam(required=false) String cursor,
                                                        @RequestParam(defaultValue="20") int size,HttpServletRequest request) {
        query(request);
        var actor=actor(principal);var page=service.list(actor,cursor,size);
        return ApiResponse.success(new PageView<>(page.items().stream().map(row->ChatSessionView.from(row,service.activeRunStatus(actor,row))).toList(),page.nextCursor(),page.hasMore()),requestId(request));
    }
    @GetMapping("/{sessionId}")
    public ApiResponse<ChatSessionView> detail(java.security.Principal principal,@PathVariable String sessionId,HttpServletRequest request) {
        var actor=actor(principal);var row=service.detail(actor,ChatService.positive(sessionId));
        return ApiResponse.success(ChatSessionView.from(row,service.activeRunStatus(actor,row)),requestId(request));
    }
    @PostMapping("/{sessionId}/archive")
    public ApiResponse<ChatSessionView> archive(java.security.Principal principal,@PathVariable String sessionId,
                                                 @RequestBody(required=false) EmptyRequest body,HttpServletRequest request) {
        if(body==null&&(request.getContentLengthLong()>0||request.getHeader(HttpHeaders.TRANSFER_ENCODING)!=null))throw ChatError.invalid();
        return ApiResponse.success(ChatSessionView.from(service.archive(actor(principal),ChatService.positive(sessionId))),requestId(request));
    }
    @GetMapping("/{sessionId}/messages")
    public ApiResponse<PageView<ChatMessageView>> history(java.security.Principal principal,@PathVariable String sessionId,
                                                           @RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size,
                                                           HttpServletRequest request) {
        query(request);var page=service.history(actor(principal),ChatService.positive(sessionId),cursor,size);
        return ApiResponse.success(new PageView<>(page.items().stream().map(ChatMessageView::from).toList(),page.nextCursor(),page.hasMore()),requestId(request));
    }
    @PostMapping(value="/{sessionId}/messages")
    public ResponseEntity<?> send(java.security.Principal principal,@PathVariable String sessionId,@RequestBody SendMessageRequest body,
                                  HttpServletRequest request) {
        String accept=request.getHeader(HttpHeaders.ACCEPT);
        if(accept!=null&&MediaType.parseMediaTypes(accept).stream().noneMatch(type->type.getQualityValue()>0
                &&(type.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)||type.isCompatibleWith(MediaType.APPLICATION_JSON))))
            throw new ChatError(406,CommonErrorCode.INVALID_ARGUMENT);
        body.validate();var accepted=service.accept(actor(principal),ChatService.positive(sessionId),body.clientMessageId(),body.content());
        if(accepted.duplicate()) {
            var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setCacheControl(CacheControl.noStore());
            headers.set(RequestIdFilter.HEADER,requestId(request));
            return new ResponseEntity<>(ApiResponse.success(ChatRunView.from(accepted),requestId(request)),headers,HttpStatus.OK);
        }
        ChatRunView run=ChatRunView.from(accepted);String requestId=requestId(request);
        SseEmitter emitter=new SseEmitter(properties.getTotalTimeout().plus(properties.getWriteTimeout()).toMillis());
        SseSession stream=new SseSession(emitter,run,requestId,properties.getStreamQueueCapacity(),properties.getHeartbeat(),properties.getWriteTimeout(),writers,timer);
        stream.startEvent();
        try {stream.start();}catch(RejectedExecutionException e){service.finish(accepted,"FAILED","","DEPENDENCY_ERROR",null);throw new ChatError(503,CommonErrorCode.DEPENDENCY_ERROR);}
        try {stream.setCall(service.execute(accepted,requestId,stream.listener()));}
        catch(ChatError error) {
            stream.error(error.getCode()==CommonErrorCode.DEPENDENCY_ERROR?"DEPENDENCY_ERROR":"CHAT_DEPENDENCY_UNAVAILABLE",error.getMessage(),"FAILED");
        } catch(RuntimeException error) {stream.error("DEPENDENCY_ERROR","模型服务不可用","FAILED");}
        var headers=new HttpHeaders();headers.setContentType(MediaType.TEXT_EVENT_STREAM);headers.setCacheControl(CacheControl.noStore());headers.set(RequestIdFilter.HEADER,requestId);
        return new ResponseEntity<>(emitter,headers,HttpStatus.OK);
    }
    private static Actor actor(java.security.Principal principal) {
        if(principal instanceof Authentication auth&&auth.getPrincipal() instanceof Actor actor)return actor;
        throw new ChatError(401,CommonErrorCode.UNAUTHORIZED);
    }
    private static String requestId(HttpServletRequest request){return (String)request.getAttribute(RequestIdFilter.ATTRIBUTE);}
    private static void query(HttpServletRequest request){
        request.getParameterMap().forEach((name,values)->{if(!Set.of("cursor","size").contains(name)||values.length!=1)throw ChatError.invalid();});
    }
    public record PageView<T>(List<T> items,String nextCursor,boolean hasMore) { }
}
