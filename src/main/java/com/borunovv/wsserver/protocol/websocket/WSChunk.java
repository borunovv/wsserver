package com.borunovv.wsserver.protocol.websocket;

import java.util.HashMap;
import java.util.Map;

class WSChunk {

    public enum Type {UNDEFINED, CONNECTION_CLOSED, PING, PONG, BINARY, UTF8, CONTINUATION}

    private static final Map<Integer, Type> types = new HashMap<>();

    static {
        // See https://tools.ietf.org/html/rfc6455#page-27 (page 28)

        types.put(0x00, Type.CONTINUATION);
        types.put(0x01, Type.UTF8);
        types.put(0x02, Type.BINARY);

        types.put(0x08, Type.CONNECTION_CLOSED);
        types.put(0x09, Type.PING);
        types.put(0x0A, Type.PONG);
    }


    public int offset;
    public int length;

    public boolean isFIN;
    public int opcode;

    public int maskOffset;
    public int payloadOffset;
    public int payloadLength;

    public boolean hasMask() {
        return maskOffset > 0;
    }

    public boolean hasPayload() {
        return payloadOffset > 0 && payloadLength > 0;
    }

    public Type getType() {
        return types.containsKey(opcode) ?
                types.get(opcode) :
                Type.UNDEFINED;
    }

    public boolean isControl() {
        return (opcode & 0x08) > 0; // PING / PONG / CONNECTION_CLOSED
    }
}
