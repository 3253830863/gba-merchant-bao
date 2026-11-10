package com.gba.merchantbao.chat.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;

public final class SendMessageRequest {
    private String content,clientMessageId;
    @JsonSetter("content") public void setContent(com.fasterxml.jackson.databind.JsonNode value){this.content=string(value);}
    @JsonSetter("clientMessageId") public void setClientMessageId(com.fasterxml.jackson.databind.JsonNode value){clientMessageId=string(value);}
    private static String string(com.fasterxml.jackson.databind.JsonNode value){if(value==null||!value.isTextual())throw new IllegalArgumentException("Invalid message request");return value.textValue();}
    @JsonAnySetter public void reject(String name,Object value){throw new IllegalArgumentException("Unknown field");}
    public void validate(){
        if(content==null||content.isBlank()||content.codePointCount(0,content.length())>4000
                ||clientMessageId==null||!clientMessageId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("Invalid message request");
    }
    public String content(){return content;}
    public String clientMessageId(){return clientMessageId;}
}
