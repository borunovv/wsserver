package com.borunovv.wsserver.nio;

import com.borunovv.wsserver.protocol.websocket.WSMessage;

public interface IWSMessageHandler {
    void handle(WSMessage msg);
}
