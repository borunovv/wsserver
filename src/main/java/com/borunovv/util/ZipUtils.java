package com.borunovv.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.*;

public final class ZipUtils {

    public static byte[] zipTextFiles(Map<String, String> files) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(bos);
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String fileName = entry.getKey();
                String content = entry.getValue();

                zos.putNextEntry(new ZipEntry(fileName));
                byte[] data = StringUtils.uft8StringToBytes(content);
                zos.write(data, 0, data.length);
                zos.closeEntry();
            }

            zos.close();
            return bos.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException("Error creating zip archive", e);
        }
    }


    public static byte[] compress(byte[] data) {
        if (data == null) return null;
        return compress(data, new byte[1024 * 10]);
    }

    public static byte[] compress(byte[] data, byte[] tmpBuffer) {
        if (data == null) return null;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        Deflater compresser = new Deflater();
        compresser.setInput(data);
        compresser.finish();

        while (!compresser.finished()) {
            int count = compresser.deflate(tmpBuffer);
            bos.write(tmpBuffer, 0, count);
        }
        compresser.end();

        return bos.toByteArray();
    }

    public static byte[] decompress(byte[] data) throws DataFormatException {
        if (data == null) return null;
        return decompress(data, new byte[1024 * 10]);
    }

    public static byte[] decompress(byte[] data, byte[] tmpBuffer) throws DataFormatException {
        if (data == null) return null;

        Inflater decompresser = new Inflater();
        decompresser.setInput(data, 0, data.length);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length * 2);
        while (!decompresser.finished()) {
            int count = decompresser.inflate(tmpBuffer);
            bos.write(tmpBuffer, 0, count);
        }

        decompresser.end();
        return bos.toByteArray();
    }


    public static byte[] compressIgnoreZeroSize(byte[] data) {
        if (data == null || data.length == 0) return data;
        return compressIgnoreZeroSize(data, new byte[1024 * 10]);
    }

    public static byte[] compressIgnoreZeroSize(byte[] data, byte[] tmpBuffer) {
        if (data == null || data.length == 0) return data;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        Deflater compresser = new Deflater();
        compresser.setInput(data);
        compresser.finish();

        while (!compresser.finished()) {
            int count = compresser.deflate(tmpBuffer);
            bos.write(tmpBuffer, 0, count);
        }
        compresser.end();

        return bos.toByteArray();
    }

    public static byte[] decompressIgnoreZeroSize(byte[] data) throws DataFormatException {
        if (data == null || data.length == 0) return data;
        return decompressIgnoreZeroSize(data, new byte[1024 * 10]);
    }

    public static byte[] decompressIgnoreZeroSize(byte[] data, byte[] tmpBuffer) throws DataFormatException {
        if (data == null || data.length == 0) return data;

        Inflater decompresser = new Inflater();
        decompresser.setInput(data, 0, data.length);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length * 10);
        while (!decompresser.finished()) {
            int count = decompresser.inflate(tmpBuffer);
            bos.write(tmpBuffer, 0, count);
        }

        decompresser.end();
        return bos.toByteArray();
    }
}
