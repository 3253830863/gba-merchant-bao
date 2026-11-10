package com.gba.merchantbao.agent.service;

import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import javax.net.ssl.*;

/** Retains the JDK transport so cancellation also aborts a call before its first SDK token callback. */
final class OwnedHttpClientBuilder implements HttpClient.Builder {
    private final HttpClient.Builder delegate = HttpClient.newBuilder();
    private volatile HttpClient client;
    @Override public HttpClient build() { client=delegate.build(); return client; }
    void stop() { var c=client; if(c!=null)c.shutdownNow(); }
    public HttpClient.Builder cookieHandler(CookieHandler v){delegate.cookieHandler(v);return this;}
    public HttpClient.Builder connectTimeout(Duration v){delegate.connectTimeout(v);return this;}
    public HttpClient.Builder sslContext(SSLContext v){delegate.sslContext(v);return this;}
    public HttpClient.Builder sslParameters(SSLParameters v){delegate.sslParameters(v);return this;}
    public HttpClient.Builder executor(Executor v){delegate.executor(v);return this;}
    public HttpClient.Builder followRedirects(HttpClient.Redirect v){delegate.followRedirects(v);return this;}
    public HttpClient.Builder version(HttpClient.Version v){delegate.version(v);return this;}
    public HttpClient.Builder priority(int v){delegate.priority(v);return this;}
    public HttpClient.Builder proxy(ProxySelector v){delegate.proxy(v);return this;}
    public HttpClient.Builder authenticator(Authenticator v){delegate.authenticator(v);return this;}
}
