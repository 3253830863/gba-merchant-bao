package com.gba.merchantbao.chat.service;
import java.util.List;
public record ChatPage<T>(List<T> items,String nextCursor,boolean hasMore) { }
