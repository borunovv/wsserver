package com.borunovv.util;

import com.borunovv.contract.Precondition;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.UUID;
import java.util.zip.CRC32;


public final class CryptUtils {

    public static long crc32(String str) {
        CRC32 cipher = new CRC32();
        cipher.update(str.getBytes());
        return cipher.getValue();
    }

    public static String encodeBase64(byte[] data){
        return StringUtils.toUtf8String(
                Base64.encodeBase64(data, false));
    }

    public static String encodeBase64(String data){
        return encodeBase64(StringUtils.uft8StringToBytes(data));
    }

    public static String encodeBase64URLSafe(byte[] data){
        return Base64.encodeBase64URLSafeString(data);
    }

    public static String encodeBase64URLSafe(String data){
        return Base64.encodeBase64URLSafeString(
                StringUtils.uft8StringToBytes(data));
    }

    public static byte[] decodeBase64(String data) {
        return Base64.decodeBase64(data);
    }

    public static String decodeBase64ToString(String data) {
        return StringUtils.toUtf8String(decodeBase64(data));
    }

    public static String md5(String data){
        return DigestUtils.md5Hex(data);
    }

    public static byte[] sha1(byte[] data) {
        return DigestUtils.sha(data);
    }

    public static String randomUUID() {
        return UUID.randomUUID().toString();
    }

    public static void xor(byte[] buff, String key) {
        Precondition.expected(buff != null, "buff is null");
        Precondition.expected(key != null && key.length() > 0, "key is null or empty");

        xor(buff, StringUtils.uft8StringToBytes(key), buff);
    }

    public static void xor(byte[] buff, byte[] key, byte[] result) {
        Precondition.expected(buff != null, "buff is null");
        Precondition.expected(key != null && key.length > 0, "key is null or empty");
        Precondition.expected(result != null, "result is null");
        Precondition.expected(result.length >= buff.length, "result size must be >= buff size");

        for (int i = 0; i < buff.length; ++i) {
            result[i] = (byte) (buff[i] ^ key[i % key.length]);
        }
    }
}