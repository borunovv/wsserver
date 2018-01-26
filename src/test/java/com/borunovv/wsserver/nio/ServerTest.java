package com.borunovv.wsserver.nio;

import com.borunovv.log.Log;
import com.borunovv.wsserver.protocol.websocket.WSMessage;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class ServerTest {

    @Test
    public void start() throws Exception {
        Server server = new Server(8888, 4, new MyHandler());
        server.start();
        Log.info("Server started on port 8888 for 5 minutes.");
        Log.info("You can open test html-page via browser (src/main/resources/html/ws_test.html).");
        // Start server for 5 minutes.
        for (int i = 0; i < 5 * 60; ++i) {
            Thread.sleep(1000);
        }
        server.stop();
        Log.info("Server stopped.");
    }

    private static class MyHandler implements IMessageHandler<WSMessage> {
        private AtomicInteger counter = new AtomicInteger();

        @Override
        public void handle(WSMessage message) {
            int requestIndex = counter.incrementAndGet();

            // Read message from client (we assume client sent us utf8 text,
            // for binary data use message.getBinaryData() and also check message.getType());
            String messageFromClientUtf8 = message.getUtf8Text();

            Log.info("Request #" + requestIndex + " come. "
                    + ", Type: " + message.getType()
                    + ", Content (utf8): '" + messageFromClientUtf8 + "'");


            // Send response to client
            String responseMsg = "Hello from server! Index #" + requestIndex;

            message.getSession().queueMessageToClient(
                    WSMessage.makeUtf8(message.getSession(), responseMsg));

            Log.info("Sent response to client: '" + responseMsg + "'");
        }

        @Override
        public void onReject(WSMessage message) {
            Log.warn("Message rejected: " + message);
        }

        @Override
        public void onError(WSMessage message, Exception cause) {
            Log.error("Error while processing message: " + message, cause);
        }

        @Override
        public void onError(Exception cause) {
            Log.error("Error while processing message", cause);
        }
    }
}