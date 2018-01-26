package com.borunovv.wsserver.protocol.http;

import com.borunovv.wsserver.nio.RWSession;
import com.borunovv.wsserver.protocol.AbstractMessage;

public class HttpMessage extends AbstractMessage {

    private HttpRequest request;
    private HttpResponse response;

    public HttpMessage(RWSession session, HttpRequest request , HttpResponse response) {
        super(session);
        this.request = request;
        this.response = response;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public void setRequest(HttpRequest request) {
        this.request = request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public void setResponse(HttpResponse response) {
        this.response = response;
    }

    public boolean hasResponse() {
        return response != null;
    }

    public boolean hasRequest() {
        return request != null;
    }

    @Override
    public String toString() {
        return "HttpMessage{" + (request != null ? request : "[null request]")
                + (response!= null ? ", " + response : "") + "}";
    }
}