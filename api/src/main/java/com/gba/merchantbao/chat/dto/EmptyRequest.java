package com.gba.merchantbao.chat.dto;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public final class EmptyRequest {
    @JsonAnySetter public void reject(String name,Object value){
        throw new IllegalArgumentException("Unknown field");
    }
}
