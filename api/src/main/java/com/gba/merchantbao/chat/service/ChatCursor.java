package com.gba.merchantbao.chat.service;

import com.gba.merchantbao.chat.exception.ChatError;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Position only; every read still reapplies the trusted owner. */
final class ChatCursor {
    static String encode(String kind,String value){return Base64.getUrlEncoder().withoutPadding().encodeToString((kind+":"+value).getBytes(StandardCharsets.US_ASCII));}
    static String decode(String kind,String cursor) {
        try {
            if(cursor==null)return null;
            if(!cursor.matches("[A-Za-z0-9_-]{1,512}"))throw ChatError.invalid();
            String decoded=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.US_ASCII);
            if(!decoded.startsWith(kind+":"))throw ChatError.invalid();
            return decoded.substring(kind.length()+1);
        } catch(RuntimeException e){throw ChatError.invalid();}
    }
}
