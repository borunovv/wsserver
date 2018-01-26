package com.borunovv.wsserver.protocol.websocket;

import com.borunovv.contract.Precondition;
import com.borunovv.util.CryptUtils;
import com.borunovv.util.StringUtils;
import com.borunovv.wsserver.nio.RWSession;
import com.borunovv.wsserver.protocol.AbstractMessage;
import com.borunovv.wsserver.protocol.http.HttpMessage;
import com.borunovv.wsserver.protocol.http.HttpRequest;
import com.borunovv.wsserver.protocol.http.HttpResponse;
import com.borunovv.wsserver.protocol.http.NonCompleteHttpRequestException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class WSMessage extends AbstractMessage {
    public enum Type {HANDSHAKE, CONNECTION_CLOSED, PING, PONG, BINARY, UTF8}

    private HttpMessage handShakeMessage;
    private Type type;
    private byte[] binaryData;
    private String utf8Text;
    private List<WSMessage> interleavedControlMessages = new ArrayList<WSMessage>(10);

    public WSMessage(RWSession session, byte[] data, int length) {
        super(session);
        unmarshall(session, data, length);
    }

    private WSMessage(RWSession session, Type type) {
        super(session);
        this.type = type;
    }

    public static WSMessage makePing(RWSession session) {
        return new WSMessage(session, Type.PING);
    }

    public static WSMessage makePong(RWSession session) {
        return new WSMessage(session, Type.PONG);
    }

    public static WSMessage makeConnectionClosed(RWSession session) {
        return new WSMessage(session, Type.CONNECTION_CLOSED);
    }

    public static WSMessage makeBinary(RWSession session, byte[] data) {
        WSMessage msg = new WSMessage(session, Type.BINARY);
        msg.binaryData = data;
        return msg;
    }

    public static WSMessage makeUtf8(RWSession session, String utf8Text) {
        WSMessage msg = new WSMessage(session, Type.UTF8);
        msg.utf8Text = utf8Text;
        return msg;
    }

    public List<WSMessage> getInterleavedControlMessages() {
        return interleavedControlMessages;
    }

    public Type getType() {
        return type;
    }

    public boolean isControlMessage() {
        return !isDataMessage();
    }

    private boolean isDataMessage() {
        return type == Type.BINARY || type == Type.UTF8;
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    public String getUtf8Text() {
        return utf8Text;
    }

    public HttpMessage getHandShakeMessage() {
        return handShakeMessage;
    }

    public byte[] marshall() {
        Precondition.expected(type != null, "Not initialized message. Type is undefined");

        switch (type) {
            case CONNECTION_CLOSED:
                return new byte[]{(byte) 0x88, (byte) 0x00};
            case PING:
                return new byte[]{(byte) 0x89, (byte) 0x00};
            case PONG:
                return new byte[]{(byte) 0x8A, (byte) 0x00};

            case BINARY:
                return marshallData(binaryData, Type.BINARY);
            case UTF8:
                return marshallData(StringUtils.uft8StringToBytes(utf8Text), Type.UTF8);

            case HANDSHAKE:
                if (!handShakeMessage.hasResponse()) {
                    throw new IllegalStateException("We can marshall only httpResponse for handshake");
                }

                return handShakeMessage.getResponse().marshall();

            default:
                throw new RuntimeException("Not allowed to marshall message with type: " + type);
        }
    }

    // see https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_servers
    private byte[] marshallData(byte[] payloadData, Type type) {
        Precondition.expected(type == Type.BINARY || type == Type.UTF8,
                "Expected types BINARY or UTF8, actual is: " + type);

        long payloadLengthLong = payloadData.length;

        int lenBytesCount = payloadLengthLong <= 125 ?
                0 :
                payloadData.length < (1 << 16) ?
                        2 :
                        8;

        int bufLen = 2 + lenBytesCount + payloadData.length;
        byte[] result = new byte[bufLen];

        byte firstByte = (byte) (type == Type.UTF8 ? 0x81 : 0x82); // FIN = 1, opcode = 1-text, 2-binary
        result[0] = firstByte;

        switch (lenBytesCount) {
            case 0:
                result[1] = (byte) (payloadLengthLong & 0xFF);
                break;
            case 2:
                result[1] = 126;
                result[2] = (byte) ((payloadLengthLong >> 8) & 0xFF);
                result[3] = (byte) (payloadLengthLong & 0xFF);
                break;
            case 8:
                result[1] = 127;
                result[2] = (byte) ((payloadLengthLong >> 56) & 0xFF);
                result[3] = (byte) ((payloadLengthLong >> 48) & 0xFF);
                result[4] = (byte) ((payloadLengthLong >> 40) & 0xFF);
                result[5] = (byte) ((payloadLengthLong >> 32) & 0xFF);
                result[6] = (byte) ((payloadLengthLong >> 24) & 0xFF);
                result[7] = (byte) ((payloadLengthLong >> 16) & 0xFF);
                result[8] = (byte) ((payloadLengthLong >> 8) & 0xFF);
                result[9] = (byte) ((payloadLengthLong >> 0) & 0xFF);
                break;
            default:
                throw new RuntimeException("Unexpected len bytes count: " + lenBytesCount);
        }

        int payloadOffset = 2 + lenBytesCount;

        System.arraycopy(payloadData, 0, result, payloadOffset, payloadData.length);
        return result;
    }

    // see https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_servers
    public static int tryParse(byte[] data, int length) {
        if (isHandshake(data, length)) {
            return HttpRequest.tryParse(data, length);
        } else {
            List<WSChunk> chunks = WSChunkParser.tryParseOneMessage(data, 0, length);
            if (chunks.isEmpty()) {
                return 0;
            }
            // Определим длину всех чанков, это и будет кусок буфера с одним целым сообщением.
            return WSChunkParser.getTotalChunksLength(chunks);
        }
    }

    private void unmarshall(RWSession session, byte[] data, int length) {
        if (isHandshake(data, length)) {
            unmarshallHandShake(session, data, length);
        } else {
            unmarshallWSMessage(data, length);
        }
    }

    private void unmarshallHandShake(RWSession session, byte[] data, int length) {
        HttpRequest handShakeRequest;
        try {
            handShakeRequest = new HttpRequest(data, length);
        } catch (NonCompleteHttpRequestException e) {
            throw new RuntimeException("Unexpected state. Bad HTTP request: \n'"
                    + StringUtils.toUtf8String(Arrays.copyOf(data, length)) + "'", e);
        }

        this.type = Type.HANDSHAKE;
        this.binaryData = null;
        this.utf8Text = null;
        this.handShakeMessage = new HttpMessage(
                session,
                handShakeRequest,
                makeHandshakeResponse(handShakeRequest));
    }

    private void unmarshallWSMessage(byte[] data, int length) {
        List<WSChunk> chunks = WSChunkParser.tryParseOneMessage(data, 0, length);
        if (chunks.isEmpty()) {
            throw new RuntimeException("Unexpected. Buffer does not contain whole websocket message.");
        }

        this.interleavedControlMessages.clear();
        this.handShakeMessage = null;
        this.binaryData = null;
        this.utf8Text = null;

        WSChunk firstDataChunk = null;
        for (WSChunk chunk : chunks) {
            if (!chunk.isControl()) {
                firstDataChunk = chunk;
                break;
            }
        }

        if (firstDataChunk != null) {
            byte[] payload = WSChunkParser.readPayload(chunks, data);

            switch (firstDataChunk.getType()) {
                case BINARY:
                    this.type = Type.BINARY;
                    this.binaryData = payload;
                    break;
                case UTF8:
                    this.type = Type.UTF8;
                    this.utf8Text = StringUtils.toUtf8String(payload);
                    break;
                default:
                    throw new RuntimeException("Unexpected first data chunk type: " + firstDataChunk.getType());
            }


            List<WSChunk> controlChunks = WSChunkParser.getControlChunksOnly(chunks);
            for (WSChunk controlChunk : controlChunks) {
                WSMessage msg = new WSMessage(getSession(), null);
                msg.fromControlChunk(controlChunk, data);
                interleavedControlMessages.add(msg);
            }
        } else {
            Precondition.expected(chunks.size() == 1, "Expected only one control chunk");
            fromControlChunk(chunks.get(0), data);
        }
    }

    private void fromControlChunk(WSChunk controlChunk, byte[] data) {
        switch (controlChunk.getType()) {
            case CONNECTION_CLOSED:
                type = Type.CONNECTION_CLOSED;
                break;
            case PING:
                type = Type.PING;
                break;
            case PONG:
                type = Type.PONG;
                break;
            default:
                throw new RuntimeException("Unexpected control chunk type: " + controlChunk.getType());
        }

        if (controlChunk.hasPayload()) {
            binaryData = Arrays.copyOfRange(data,
                    controlChunk.payloadOffset,
                    controlChunk.payloadOffset + controlChunk.payloadLength);
        }
    }

    private static boolean isHandshake(byte[] data, int length) {
        return length < 3 ||
                (data[0] == (byte) 'G'
                        && data[1] == (byte) 'E'
                        && data[2] == (byte) 'T'); // GET
    }

    private static HttpResponse makeHandshakeResponse(HttpRequest handShakeRequest) {
        String wsKey = handShakeRequest.getSingleHeader("Sec-WebSocket-Key");
        String secretGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String keyWithGUID = wsKey + secretGUID;
        byte[] sha1 = CryptUtils.sha1(StringUtils.uft8StringToBytes(keyWithGUID));
        String base64encodedSha1 = CryptUtils.encodeBase64(sha1);

        HttpResponse response = new HttpResponse(101);
        response.setHeader("Upgrade", "websocket");
        response.setHeader("Connection", "Upgrade");
        response.setHeader("Sec-WebSocket-Accept", base64encodedSha1);

        if (handShakeRequest.hasHeader("Sec-WebSocket-Protocol")) {
            response.setHeader("Sec-WebSocket-Protocol",
                    handShakeRequest.getSingleHeader("Sec-WebSocket-Protocol"));
        }

        if (handShakeRequest.hasHeader("Origin")) {
            // Позволяем стучаться к нам с любых доменов == CORS (Cross-origin resource sharing)
            // см. https://en.wikipedia.org/wiki/Cross-origin_resource_sharing
            response.setHeader("Access-Control-Allow-Origin", "*");
        }

        return response;
    }

    @Override
    public String toString() {
        String content = makeContentPreview();
        return type + (content.isEmpty() ? "" : ": " + content);
    }

    private String makeContentPreview() {
        int maxDataLen = 500;
        String res = "";
        int contentLen = 0;
        if (type == Type.BINARY && binaryData != null) {
            res = "[";
            int len = Math.min(binaryData.length, maxDataLen);
            for (int i = 0; i < len; ++i) {
                res += binaryData[i] + ",";
            }
            if (len < binaryData.length) {
                res += "...]";
            } else {
                res += "]";
            }
            contentLen = binaryData.length;
        } else if (type == Type.UTF8 && utf8Text != null) {
            res = "'" + (utf8Text.length() > maxDataLen ?
                    (utf8Text.substring(0, maxDataLen) + "...") :
                    utf8Text)
                    + "'";
            contentLen = utf8Text.length();
        }

        if (contentLen > 0) {
            res += ", size: " + contentLen;
        }

        return res;
    }
}
