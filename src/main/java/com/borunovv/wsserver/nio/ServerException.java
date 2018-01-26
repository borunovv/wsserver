package com.borunovv.wsserver.nio;


class ServerException extends RuntimeException {
    ServerException(String message) {
        super(message);
    }

    ServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
