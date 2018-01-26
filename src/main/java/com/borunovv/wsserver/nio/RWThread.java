package com.borunovv.wsserver.nio;

import com.borunovv.contract.Precondition;
import com.borunovv.log.Log;
import com.borunovv.util.IOUtils;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;


public class RWThread extends ServerThread {
    private static final long HEART_BIT_DELAY_MS = 5;

    private Selector rwSelector;
    private ConcurrentLinkedQueue<SocketChannel> newClients = new ConcurrentLinkedQueue<>();
    private IWSMessageHandler requestHandler;
    private long lastHeartBitTime = 0;

    public void add(SocketChannel client) {
        newClients.add(client);
    }

    public RWThread(IWSMessageHandler requestHandler) {
        Precondition.expected(requestHandler != null, "requestHandler is null");
        this.requestHandler = requestHandler;
    }

    @Override
    protected void onThreadStart() {
        try {
            rwSelector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize R/W NIO thread", e);
        }
    }

    @Override
    protected void doThreadIteration() {
        try {
            registerNewClients();
            processReadyClients();
            broadcastHeartBitIfNeed();
        } catch (Exception e) {
            onThreadError(e);
        }
    }

    @Override
    protected void onThreadStop() {
        closeAllSessions(rwSelector);
        IOUtils.close(rwSelector);
        rwSelector = null;
    }

    @Override
    protected void onThreadError(Exception e) {
        Log.error("Error in R/W thread:", e);
    }

    private void registerNewClients() {
        long start = System.currentTimeMillis();
        SocketChannel client;
        while ((client = newClients.poll()) != null) {
            registerClientInSelector(client);
            if (System.currentTimeMillis() - start > HEART_BIT_DELAY_MS) {
                break;
            }
        }
    }

    private void registerClientInSelector(SocketChannel client) {
        RWSession session = new RWSession(client, requestHandler);
        try {
            client.register(rwSelector, session.getSelectionKeyFlags(), session);
        } catch (ClosedChannelException e) {
            throw new RuntimeException("Failed to register client in R/W selector", e);
        }
    }

    private void processReadyClients() {
        List<SelectionKey> keys = selectReadyClients();
        for (SelectionKey key : keys) {
            if (isStopRequested()) {
                break;
            }
            doTransferData(key);
        }
    }

    private List<SelectionKey> selectReadyClients() {
        try {
            int count = rwSelector.select(HEART_BIT_DELAY_MS);
            return count > 0 ?
                    getValidKeysOnly(rwSelector.selectedKeys()) :
                    Collections.<SelectionKey>emptyList();
        } catch (IOException e) {
            throw new RuntimeException("RW NIO thread: failed to select next portion of clients", e);
        }
    }

    private void doTransferData(SelectionKey key) {
        RWSession session = getSession(key);
        SocketChannel client = (SocketChannel) key.channel();

        try {
            if (key.isReadable() && key.isValid()) {
                session.onCanRead(key, client);
            }
            if (key.isWritable() && key.isValid()) {
                session.onCanWrite(key, client);
            }
        } catch (ClientClosedException e) {
            Log.trace("NIO RW Thread: Client disconnected (" + session.getClientRemoteAddress() + ")");
            closeClient(key);
        } catch (Exception e) {
            Log.error("NIO RW Thread: Client error. Force to close connection ["
                    + session.getClientRemoteAddress() + "]", e);
            closeClient(key);
        }
    }

    private void closeAllSessions(Selector rwSelector) {
        Set<SelectionKey> allKeys = getAllSelectionKeys(rwSelector);
        for (SelectionKey key : allKeys) {
            closeClient(key);
        }

        SocketChannel client;
        while ((client = newClients.poll()) != null) {
            IOUtils.close(client);
        }
    }

    private Set<SelectionKey> getAllSelectionKeys(Selector rwSelector) {
        Set<SelectionKey> allKeys = null;
        while (allKeys == null) {
            try {
                allKeys = new HashSet<>(rwSelector.keys());
            } catch (ConcurrentModificationException ignore) {
            }
        }
        return allKeys;
    }

    private void closeClient(SelectionKey key) {
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException ignore) {
        }
    }

    private List<SelectionKey> getValidKeysOnly(Set<SelectionKey> keys) {
        List<SelectionKey> result = new ArrayList<>(keys.size());
        Iterator<SelectionKey> iterator = keys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            if (key.isValid()) {
                result.add(key);
            }
        }
        return result;
    }

    private void broadcastHeartBitIfNeed() {
        if (System.currentTimeMillis() - lastHeartBitTime < HEART_BIT_DELAY_MS) {
            return;
        }

        try {
            Set<SelectionKey> allKeys = getAllSelectionKeys(rwSelector);
            for (SelectionKey key : allKeys) {
                RWSession session = getSession(key);
                if (key.isValid()) {
                    try {
                        session.onHeartBit(key);
                    } catch (Exception e) {
                        onThreadError(e);
                    }
                }
            }
        } finally {
            lastHeartBitTime = System.currentTimeMillis();
        }
    }

    private RWSession getSession(SelectionKey key) {
        return (RWSession) key.attachment();
    }

}
