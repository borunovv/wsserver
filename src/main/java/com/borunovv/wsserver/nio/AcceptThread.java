package com.borunovv.wsserver.nio;

import com.borunovv.log.Log;
import com.borunovv.util.IOUtils;
import com.borunovv.util.NIOUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

class AcceptThread extends ServerThread {

    private final int port;
    private final int acceptQueueSize;

    private ServerSocketChannel serverSocketChannel;
    private Selector acceptSelector;
    private Consumer<SocketChannel> clientConsumer;

    public AcceptThread(int port, int acceptQueueSize, Consumer<SocketChannel> clientConsumer) {
        this.port = port;
        this.acceptQueueSize = acceptQueueSize;
        this.clientConsumer = clientConsumer;
    }

    @Override
    protected void onThreadStart() {
        try {
            acceptSelector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            ServerSocket ss = serverSocketChannel.socket();
            ss.bind(new InetSocketAddress(port), acceptQueueSize);
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(acceptSelector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            throw new ServerException("Failed to start accept thread", e);
        }
    }

    @Override
    protected void onThreadStop() {
        IOUtils.close(acceptSelector);
        IOUtils.close(serverSocketChannel);
        acceptSelector = null;
        serverSocketChannel = null;
    }

    @Override
    protected void onThreadError(Exception e) {
        Log.error("Error in accept thread.", e);
    }

    @Override
    protected void doThreadIteration() {
        try {
            int count = acceptSelector.select(100);
            if (count > 0) {
                List<SelectionKey> keys = getValidAcceptableKeysOnly(acceptSelector.selectedKeys());
                for (SelectionKey key : keys) {
                    acceptClient(key);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to accept next portion of clients", e);
        }
    }

    private List<SelectionKey> getValidAcceptableKeysOnly(Set<SelectionKey> allKeys) {
        List<SelectionKey> result = new ArrayList<>(allKeys.size());

        Iterator<SelectionKey> iterator = allKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            if (key.isValid() && key.isAcceptable()) {
                result.add(key);
            }
        }

        return result;
    }

    private void acceptClient(SelectionKey key) {
        SocketChannel client = null;
        try {
            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
            client = serverChannel.accept();

            client.configureBlocking(false);
            client.socket().setKeepAlive(true);
            client.socket().setTcpNoDelay(true);

            clientConsumer.accept(client);
        } catch (IOException e) {
            String clientIpAddress = NIOUtils.tryGetRemoteIpAddress(client);
            IOUtils.close(client);
            onThreadError(new IOException("Failed to accept client (" + clientIpAddress + ")", e));
        }
    }
}