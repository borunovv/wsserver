package com.borunovv.wsserver.protocol.websocket;

import com.borunovv.util.DebugUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


final class WSChunkParser {

    /**
     * Парсит поток данных насколько может.
     * Вернет пустой список чанков, если в потоке находится неполное
     * сообщение иначе вернет все чанки сообщения, включая (теоритически возможные)
     * управляющие сообщения (усправляющие всегда занимают ровно 1 чанк) типа "пинг"/"разрыв связи"
     * и т.п., вкрапленные между data-чанками.
     *
     * @param fromIndex inclusive
     * @param toIndex   exclusive
     */
    static List<WSChunk> tryParseOneMessage(byte[] data, int fromIndex, int toIndex) {
        List<WSChunk> chunks = tryParse(data, fromIndex, toIndex);
        return isCompletedMessage(chunks) ?
                chunks :
                Collections.emptyList();
    }


    /**
     * Вернет суммарную длину буфера, занятую данными чанками.
     */
    static int getTotalChunksLength(List<WSChunk> chunks) {
        int total = 0;
        for (WSChunk chunk : chunks) {
            total += chunk.length;
        }
        return total;
    }


    /**
     * Соберет по кусочкам весь payload.
     */
    static byte[] readPayload(List<WSChunk> chunks, byte[] data) {
        int payloadLength = 0;
        for (WSChunk chunk : chunks) {
            payloadLength += chunk.payloadLength;
        }

        byte[] payloadData = new byte[payloadLength];
        int offset = 0;
        for (WSChunk chunk : chunks) {
            if (!chunk.isControl() && chunk.hasPayload()) {
                if (chunk.hasMask()) {
                    // Decode using XOR mask.
                    for (int i = 0; i < chunk.payloadLength; ++i) {
                        payloadData[offset] = (byte) (data[chunk.payloadOffset + i] ^ data[chunk.maskOffset + i % 4]);
                        offset++;
                    }
                } else if (chunk.hasPayload()) {
                    System.arraycopy(data, chunk.payloadOffset, payloadData, offset, chunk.payloadLength);
                    offset += chunk.payloadLength;
                }
            }
        }

        return payloadData;
    }

    /**
     * Выберет только управляющие фреймы из потока.
     * (см. https://tools.ietf.org/html/rfc6455#section-5.5)
     */
    static List<WSChunk> getControlChunksOnly(List<WSChunk> chunks) {
        List<WSChunk> controlChunks = new LinkedList<>();

        for (WSChunk chunk : chunks) {
            if (chunk.isControl()) {
                controlChunks.add(chunk);
            }
        }

        return controlChunks;
    }


    /**
     * Вернет true, если данная последовательность чанков содержит полное сообщение.
     */
    private static boolean isCompletedMessage(List<WSChunk> chunks) {
        return !chunks.isEmpty()
                && chunks.get(chunks.size() - 1).isFIN;
    }


    /**
     * Парсит поток данных насколько может.
     *
     * @param fromIndex inclusive
     * @param toIndex   exclusive
     */
    private static List<WSChunk> tryParse(byte[] data, int fromIndex, int toIndex) {
        List<WSChunk> chunks = new ArrayList<>();
        int offset = fromIndex;

        while (offset < toIndex) {
            WSChunk chunk = readChunk(data, offset, toIndex);
            if (chunk != null) {
                chunks.add(chunk);
                if (chunk.isControl() && chunks.size() == 1) {
                    break;
                }

                if (!chunk.isControl() && chunk.isFIN) {
                    break;
                }

                offset += chunk.length;
            } else {
                break;
            }
        }

        return chunks;
    }


    private static WSChunk readChunk(byte[] data, int fromIndex, int toIndex) {
        if (toIndex <= fromIndex) {
            return null;
        }
        int offset = fromIndex;

        WSChunk result = new WSChunk();
        result.offset = fromIndex;

        int firstByte = data[offset++] & 0xFF;
        result.isFIN = (firstByte & 0x80) > 0;
        result.opcode = firstByte & 0x0F;

        if (result.getType() == WSChunk.Type.UNDEFINED) {
            throw new RuntimeException("Undefined opcode: " + result.opcode
                    + "\nChunk data:\n" + DebugUtils.toString(data, fromIndex, toIndex));
        }

        if (offset >= toIndex) {
            return null;
        }

        int secondByte = data[offset++] & 0xFF;
        boolean hasMask = (secondByte & 0x80) > 0;
        int len7 = secondByte & 0x7F;

        if (len7 <= 125) {
            result.payloadLength = len7;

        } else if (len7 == 126) {
            result.payloadLength = readPayloadLength(data, offset, toIndex, 2);
            if (result.payloadLength < 0) {
                return null;
            }
            offset += 2;

        } else if (len7 == 127) {
            result.payloadLength = readPayloadLength(data, offset, toIndex, 8);
            if (result.payloadLength < 0) {
                return null;
            }
            offset += 8;
        } else {
            throw new RuntimeException("Illegal state, len7 = " + len7);
        }

        if (hasMask) {
            result.maskOffset = offset;
            offset += 4;
        }

        result.payloadOffset = offset;

        if (result.payloadOffset + result.payloadLength > toIndex) {
            return null;
        }

        offset += result.payloadLength;
        result.length = offset - result.offset;

        return result;
    }


    private static int readPayloadLength(byte[] data, int fromIndex, int toIndex, int bytesCount) {
        if (fromIndex + bytesCount > toIndex) {
            return -1;
        }

        long result = 0;
        for (int i = 0; i < bytesCount; ++i) {
            int curByte = data[fromIndex + i] & 0xFF;
            result = (result << 8) | curByte;
        }
        if (result > 0x7FFFFFFFL) {
            throw new RuntimeException("Too big payload size: " + result);
        }
        return (int) result;
    }
}
