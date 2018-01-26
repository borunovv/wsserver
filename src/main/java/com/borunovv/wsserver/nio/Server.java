package com.borunovv.wsserver.nio;

import com.borunovv.contract.Precondition;
import com.borunovv.log.Log;
import com.borunovv.util.NIOUtils;
import com.borunovv.wsserver.protocol.websocket.WSMessage;

import java.nio.channels.SocketChannel;
import java.util.function.Consumer;


public class Server implements Consumer<SocketChannel>, IWSMessageHandler {
    private static final int ACCEPT_QUEUE_SIZE = 100;
    private static final int PROCESSOR_QUEUE_WAIT_TIMEOUT_MS = 100;
    private static final int PROCESSOR_QUEUE_CAPACITY = 10000;

    private AcceptThread acceptThread;
    private RWThread rwThread;
    private ConcurrentMessageProcessor<WSMessage> messageProcessor;

    public Server(int port, int threadsCount,
                  IMessageHandler<WSMessage> messageHandler) {
        Precondition.expected(port > 0 && port <= 0xFFFF, "port must be in (1..65535)");
        Precondition.expected(messageHandler != null, "messageHandler must be non null");

        this.acceptThread = new AcceptThread(port, ACCEPT_QUEUE_SIZE, this);
        this.rwThread = new RWThread(this);
        this.messageProcessor = new ConcurrentMessageProcessor<>(
                threadsCount,
                PROCESSOR_QUEUE_CAPACITY,
                PROCESSOR_QUEUE_WAIT_TIMEOUT_MS,
                messageHandler);
    }

    public void start() throws ServerException {
        stop();
        messageProcessor.start();
        rwThread.start();
        acceptThread.start();
    }

    public void stop() {
        acceptThread.stop();
        rwThread.stop();
        messageProcessor.stop();
    }

    public boolean isRunning() {
        return acceptThread.isRunning() || rwThread.isRunning();
    }

    @Override
    public void accept(SocketChannel client) {
        String ip = NIOUtils.tryGetRemoteIpAddress(client);
        int port = NIOUtils.tryGetRemotePort(client);
        Log.info("New client connected: [" + ip + "]:" + port);
        rwThread.add(client);
    }

    @Override
    public void handle(WSMessage msg) {
        messageProcessor.accept(msg);
    }
}
