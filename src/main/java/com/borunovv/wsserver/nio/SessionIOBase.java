package com.borunovv.wsserver.nio;

import com.borunovv.contract.Precondition;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class SessionIOBase {

    private static final int INITIAL_BUFFER_SIZE = 1024;

    private static final int MAX_PACKET_SIZE = 1024 * 1024 * 10; // 10Mb
    private static final int COMMON_PACKET_SIZE = 1024; // 1Kb


    RWSession session;
    private ByteBuffer workBuffer = ByteBuffer.allocate(INITIAL_BUFFER_SIZE);


    SessionIOBase(RWSession session) {
        Precondition.expected(session != null, "session must not be null");
        this.session = session;
    }

    ByteBuffer getWorkBuffer() {
        return workBuffer;
    }

    // Вернет флажки для асинхронного I/O (типа OP_READ | OP_WRITE)
    // Чтобы система в след. раз для данной сесси уведомила нас о готовности читать/писать в клиента.
    public abstract int getSelectionKeyFlags();

    /**
     * Увеличивает размер рабочего буфера.
     * Вызывается серваком, когда пакет от клиента не помещается в рабочий буфер.
     */
    ByteBuffer enlargeWorkBuffer(int preferredSize) throws IOException {
        if (workBuffer.capacity() >= preferredSize) {
            // У нас и так размер больше, чем просят. Ничего не делаем.
            return workBuffer;
        }

        int newSize = Math.min(preferredSize, getMaxPacketSize());
        if (workBuffer.capacity() < newSize) {
            ByteBuffer newBuffer = ByteBuffer.allocate(newSize);
            workBuffer.flip();
            newBuffer.put(workBuffer);
            workBuffer = newBuffer;
            return workBuffer;
        } else {
            throw new IOException(
                    "Maximal work buffer limit reached (too big packet). Max allowed size: "
                            + getMaxPacketSize() + " bytes");
        }
    }

    ByteBuffer resetWorkBufferSizeIfNeed() {
        if (workBuffer.capacity() > getCommonPacketSize()) {
            workBuffer = ByteBuffer.allocate(getCommonPacketSize());
        }
        return workBuffer;
    }

    void putAndPrepareForRead(byte[] data, int length) throws IOException {
        if (workBuffer.capacity() < length) {
            if (length > getMaxPacketSize()) {
                throw new IOException(
                        "Maximal work buffer limit reached (too big packet). Max allowed size: "
                                + getMaxPacketSize() + " bytes");
            }
            workBuffer = ByteBuffer.allocate(length);
        }

        workBuffer.clear();
        workBuffer.put(data, 0, length);
        workBuffer.flip();
    }

    private int getMaxPacketSize() {
        return MAX_PACKET_SIZE;
    }

    private int getCommonPacketSize() {
        return COMMON_PACKET_SIZE;
    }
}
