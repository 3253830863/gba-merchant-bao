package com.gba.merchantbao.chat.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;

public final class SessionCreateRequest {
    private String title;
    private boolean titlePresent;
    @JsonSetter("title") public void setTitle(com.fasterxml.jackson.databind.JsonNode title){
        titlePresent=true;if(title==null||!title.isTextual())throw new IllegalArgumentException("Invalid title");this.title=title.textValue();
    }
    @JsonAnySetter public void reject(String name,Object value){throw new IllegalArgumentException("Unknown field");}
    public void validate(){if(titlePresent&&title==null)throw new IllegalArgumentException("Invalid title");if(title!=null){title=title.strip();if(title.codePointCount(0,title.length())>200)throw new IllegalArgumentException("Invalid title");}}
    public String title(){return title;}
}
