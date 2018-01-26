package com.borunovv.wsserver.nio;

import com.borunovv.contract.Precondition;

import com.borunovv.log.Log;
import com.borunovv.wsserver.protocol.websocket.WSMessage;
import com.borunovv.wsserver.protocol.websocket.WSProtocol;
import com.borunovv.util.IOUtils;
import com.borunovv.util.NIOUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

public class RWSession {
    private SocketChannel channel;
    private WSProtocol protocol = new WSProtocol();
    private IWSMessageHandler requestHandler;
    private SessionReader sessionReader = new SessionReader(this);
    private SessionWriter sessionWriter = new SessionWriter(this);
    private volatile boolean closeRequested;
    private volatile boolean sessionClosed;
    private String forwardedIp; // IP from X-Forwarded-For header (реальный ip клиента в случае если имеем дело с прокси)
    private ConcurrentHashMap<String, Object> keyValueStorage = new ConcurrentHashMap<>();

    RWSession(SocketChannel channel, IWSMessageHandler requestHandler) {
        Precondition.expected(channel != null, "channel is null");
        Precondition.expected(requestHandler != null, "requestHandler is null");

        this.channel = channel;
        this.requestHandler = requestHandler;
    }

    String getClientRemoteAddress() {
        return useProxi() ?
                forwardedIp :
                NIOUtils.tryGetRemoteIpAddress(channel);
    }

    public String getPrettyClientRemoteAdressAndPort() {
        return "[" + getClientRemoteAddress() + "]:" + getClientRemotePort();
    }

    private int getClientRemotePort() {
        return NIOUtils.tryGetRemotePort(channel);
    }

    private String getClientForwardedRemoteAddress() {
        return forwardedIp;
    }

    private boolean useProxi() {
        return forwardedIp != null;
    }

    public WSProtocol getProtocol() {
        return protocol;
    }

    public void close() {
        closeRequested = true;
    }

    private void close(SelectionKey key) {
        sessionClosed = true;
        key.cancel();
        IOUtils.close(key.channel());
        closeRequested = true;
    }

    public boolean isClosed() {
        return sessionClosed || closeRequested;
    }

    public void queueMessageToClient(WSMessage msg) {
        if (sessionWriter.canQueuePacket()) {
            byte[] rawData = getProtocol().marshall(msg);
            boolean isSuccess = sessionWriter.queuePacket(ByteBuffer.wrap(rawData), msg);
            if (!isSuccess) {
                throw new RuntimeException("Failed to queue output message (queue is full). Client ip: ["
                        + getClientRemoteAddress() + "]\n" + msg);
            }
        }
    }

    int getSelectionKeyFlags() {
        return sessionReader.getSelectionKeyFlags()
                | sessionWriter.getSelectionKeyFlags();
    }

    void onHeartBit(SelectionKey selectionKey) {
        if (closeRequested) {
            close(selectionKey);
        } else {
            sessionReader.onHeartBit(selectionKey);
            selectionKey.interestOps(getSelectionKeyFlags());
        }
    }

    void onCanRead(SelectionKey key, SocketChannel client) throws IOException {
        sessionReader.onCanRead(key, client);
    }

    void onCanWrite(SelectionKey key, SocketChannel client) throws IOException {
        sessionWriter.onCanWrite(key, client);
    }

    void onPacketStart(SelectionKey key) {

    }

    void onPacketFinish(SelectionKey key, byte[] data, int length) {
        WSMessage msg = protocol.unmarshall(this, data, length);
        if (!msg.isControlMessage()) {
            requestHandler.handle(msg);
        }
    }

    void onPacketSent(SelectionKey key, Object customDataAssociatedWithPacket, int packetSize) {
    }

    /**
     * Задает реальный IP клиента.
     * Используется когда соединение через прокси (apache/nginx). Тут будет реальный ip клиента.
     */
    public void setForwardedIp(String forwardedIp) {
        this.forwardedIp = forwardedIp;
    }

    void onPacketSentFailed(SelectionKey key, Object customDataAssociatedWithPacket, Throwable cause) {
        Log.error("Server internal error: failed to send packet", cause);
    }

    public Object getValueByKey(String key) {
        return keyValueStorage.get(key);
    }

    public Object setValueByKey(String key, Object value) {
        return keyValueStorage.put(key, value);
    }
}
