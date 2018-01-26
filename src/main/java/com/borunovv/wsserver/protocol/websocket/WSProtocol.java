package com.borunovv.wsserver.protocol.websocket;

import com.borunovv.wsserver.nio.RWSession;

import java.nio.ByteBuffer;


public class WSProtocol {

    public int checkPacket(ByteBuffer buffer) {
        ByteBuffer buff = buffer.duplicate();
        buff.flip();

        byte[] data = buff.array();
        int length = buff.limit();

        return WSMessage.tryParse(data, length);
    }

    public byte[] marshall(WSMessage msg) {
        return msg.marshall();
    }

    public WSMessage unmarshall(RWSession session, byte[] data, int length) {
        WSMessage msg = new WSMessage(session, data, length);
        if (msg.isControlMessage()) {
            processControlMessage(session, msg);
        } else {
            for (WSMessage controlMsg : msg.getInterleavedControlMessages()) {
                processControlMessage(session, controlMsg);
            }
        }
        return msg;
    }

    private void processControlMessage(RWSession session, WSMessage msg) {
        switch (msg.getType()) {
            case HANDSHAKE:
                // Обработаем X-Forwarded-For header чтобы узнать настоящий ip клиента.
                if (msg.getHandShakeMessage() != null) {
                    String xForwardedIp = msg.getHandShakeMessage().getRequest().getSingleHeader("X-Forwarded-For");
                    if (xForwardedIp != null) {
                        session.setForwardedIp(xForwardedIp);
                    }
                }
                session.queueMessageToClient(msg);
                break;
            case CONNECTION_CLOSED:
                session.queueMessageToClient(WSMessage.makeConnectionClosed(session));
                break;
            case PING:
                session.queueMessageToClient(WSMessage.makePong(session));
                break;
            case PONG:
                break;
            default:
                throw new RuntimeException("Unexpected control message type: " + msg.getType());
        }
    }

}
