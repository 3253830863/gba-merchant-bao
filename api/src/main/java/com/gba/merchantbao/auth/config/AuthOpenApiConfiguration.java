package com.gba.merchantbao.auth.config;

import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.*;
import java.util.Map;

@Configuration(proxyBeanMethods=false)
public class AuthOpenApiConfiguration {
    @Bean GroupedOpenApi authV1OpenApi() {
        return GroupedOpenApi.builder().group("auth-v1").packagesToScan("com.gba.merchantbao.auth.controller")
            .pathsToMatch("/api/v1/auth/**","/api/v1/stores/*/auth/**","/api/v1/staff-accounts/**","/api/v1/consumer-accounts/**").addOpenApiCustomizer(api->{
                api.getInfo().setDescription("认证实现快照：9 操作。Bearer access 或 refresh body；所有响应 no-store。生产首员工、真实短信及前端另行交付。");
                api.setServers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/")));
                Schema<?> envelope=new ObjectSchema().addProperty("code",new IntegerSchema()).addProperty("message",new StringSchema())
                    .addProperty("data",new Schema<>().nullable(true)).addProperty("requestId",new StringSchema());
                api.getComponents().addSchemas("AuthErrorEnvelope",envelope);
                Map<String,String> errors=Map.of("400","10001 参数无效；11002 验证码无效", "401","10002 会话无效；11001 凭证无效",
                    "403","10003 角色越权", "404","10004 资源不存在或越界", "409","10009 员工标识冲突",
                    "429","11003 限流", "503","11501 依赖不可用；11502 短信 disabled/发送失败", "500","10500 非预期错误");
                api.getPaths().values().forEach(path->path.readOperations().forEach(op->{
                    if("createStaffAccount".equals(op.getOperationId())) {
                        var created=op.getResponses().get("201");
                        created.content(new Content().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiResponseAccountView"))));
                        created.addHeaderObject("Location",new Header().description("/api/v1/staff-accounts/{accountId}").schema(new StringSchema()));
                        op.getResponses().remove("200");
                    }
                    errors.forEach((status,description)->op.getResponses().addApiResponse(status,new ApiResponse().description(description)
                        .content(new Content().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/AuthErrorEnvelope"))))));
                    op.getResponses().forEach((status,response)->{
                        response.addHeaderObject("X-Request-Id",new Header().description("与 envelope.requestId 相同").schema(new StringSchema()));
                        response.addHeaderObject("Cache-Control",new Header().schema(new StringSchema()._const("no-store")));
                        if(status.equals("429"))response.addHeaderObject("Retry-After",new Header().description("剩余窗口秒数").schema(new IntegerSchema()));
                    });
                }));
            }).build();
    }
}
