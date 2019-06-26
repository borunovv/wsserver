# wsserver

Welcome!

This is lightweight but robust websocket (RFC 6455) server written from scratch (core java, thread pools, NIO and without any 3rd party frameworks).

Please feel free to use my code for any goals.

Usage example:

```
import com.borunovv.log.Log;
import com.borunovv.wsserver.protocol.websocket.WSMessage;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerTest {
    
    public void start() throws Exception {
        Server server = new Server(8888, 4, new MyHandler());
        server.start();
        Log.info("Server started on port 8888 for 5 minutes.");
        Log.info("You can open test html-page via browser (/src/main/resources/html/ws_test.html)" 
            + " with JavaScript code for sending messages.");
        
        // Wait 5 minutes and then stop it.
        Thread.sleep(5 * 1000);
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
```
