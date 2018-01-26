package com.borunovv.wsserver.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * Хелпер по асинхронному чтению из сокета.
 * Нарезает трафик на пакеты.
 */
public class SessionReader extends SessionIOBase {

    private volatile boolean readingNow = false;

    SessionReader(RWSession session) {
        super(session);
    }

    void onCanRead(SelectionKey key, SocketChannel client) throws IOException {
        doRead(key, client);
    }

    private boolean isReadingNow() {
        return readingNow;
    }

    /**
     * Читает очередной кусочек данных из клиента.
     */
    private void doRead(SelectionKey key, SocketChannel client) throws IOException {
        ByteBuffer buffer = getWorkBuffer();
        boolean bufferWasEmpty = (buffer.position() == 0);

        if (bufferWasEmpty && client == null) {
            return;
        }

        int len = 0;
        if (client != null) {
            try {
                len = client.read(buffer);
                if (len == -1) {
                    throw new ClientClosedException("Client closed (can't read)");
                }
            } catch (IOException e) {
                throw new ClientClosedException("Client I/O error", e);
            }
        }

        boolean somethingRed = (len > 0);
        if (bufferWasEmpty && somethingRed) {
            readingNow = true;
            session.onPacketStart(key);
        }

        if (somethingRed || (!bufferWasEmpty && client == null)) {
            int correctPacketLen = getCorrectPacketLength(buffer);

            if (correctPacketLen > 0) { // Критерий наличия в рабочем буфере хотябы одного целого пакета.
                buffer.flip();  // prepare to read.
                session.onPacketFinish(key, buffer.array(), correctPacketLen);
                if (buffer.limit() == correctPacketLen) {
                    // В буфере кроме только что прочитанного пакета больше ничего нет
                    // (т.е. никаких кусков от следующего пакета)
                    // Можно вернуть размер буфера на дефолтный, если он больше.
                    buffer = resetWorkBufferSizeIfNeed();
                    buffer.clear();
                    readingNow = false;
                } else {
                    // В буфере есть еще куски от нового пакета(ов), т.е. мы не можем их потерять.
                    // Двигаем позицию к началу нового куска.
                    buffer.position(correctPacketLen);
                    // Смещаем новый кусок в начало буфера
                    // (чтобы прочитанный пакет больше не занимал место).
                    buffer.compact();

                    readingNow = true;
                    session.onPacketStart(key);
                }
            }
        }

        if (buffer.remaining() == 0) {
            // Не хватает буфера, надо переаллоцировать
            enlargeWorkBuffer(buffer.capacity() * 2);
        }
    }

    @Override
    public int getSelectionKeyFlags() {
        return SelectionKey.OP_READ;
    }

    /**
     * Проверяет, есть ли в буфере уже цельный пакет, и если да, то вернет его длину.
     * Иначе вернет 0.
     */
    private int getCorrectPacketLength(ByteBuffer buffer) {
        return session.getProtocol().checkPacket(buffer);
    }

    void onHeartBit(SelectionKey key) {
        if (isReadingNow()) {
            try {
                doRead(key, null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
