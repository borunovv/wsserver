package com.borunovv.wsserver.protocol.http;


public class NonCompleteHttpRequestException extends Exception {
    NonCompleteHttpRequestException(String message) {
        super(message);
    }
}