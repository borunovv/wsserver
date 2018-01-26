package com.borunovv.wsserver.protocol.http;

import com.borunovv.util.StringUtils;
import com.borunovv.util.ZipUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


public class HttpResponse {

    private static final String HDR_LINE_DELIM = "\r\n";

    private int status;
    private Map<String, String> headers = new HashMap<String, String>();
    private byte[] content;

    public HttpResponse(int status) {
        this.status = status;
        disableCachingOnClientSide(); // Чтобы клиент не кэшировал.
    }

    public HttpResponse(int status, byte[] content) {
        this(status);
        this.content = content;
    }

    public HttpResponse(int status, Map<String, String> headers, byte[] content) {
        this(status, content);
        this.headers = headers != null ?
                headers :
                new HashMap<>();
    }

    public HttpResponse setStatus(int status) {
        this.status = status;
        return this;
    }

    public HttpResponse setContent(byte[] content, String contentType) {
        this.content = content;
        setHeader("Content-Type", contentType);
        return this;
    }

    public HttpResponse setCompressedContent(byte[] notCompressedContent, String contentType) {
        setContent(ZipUtils.compress(notCompressedContent == null ?
                        new byte[0] :
                        notCompressedContent),
                contentType);
        setHeader("Content-Encoding", "deflate");
        return this;
    }

    public HttpResponse setCompressedContent(String notCompressedContent, String contentType) {
        return setCompressedContent(notCompressedContent == null ?
                        null :
                        StringUtils.uft8StringToBytes(notCompressedContent),
                contentType);
    }

    public HttpResponse setHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public void setRedirect(String url) {
        setStatus(302);
        setHeader("Location", url);
    }

    public void writeHtml(String html) {
        setContent(StringUtils.uft8StringToBytes(html), ContentType.HTML);
    }

    public void writePlainText(String text) {
        setContent(StringUtils.uft8StringToBytes(text), ContentType.TEXT);
    }

    public void writeJson(String json) {
        setContent(StringUtils.uft8StringToBytes(json), ContentType.JSON);
    }

    public byte[] marshall() {
        String header = "HTTP/1.1 " + status + " " + getStatusText(status) + HDR_LINE_DELIM;
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                header += entry.getKey() + ": " + entry.getValue() + HDR_LINE_DELIM;
            }
        }
        header += "Content-Length: " + (content != null ? content.length : 0) + HDR_LINE_DELIM;
        header += HDR_LINE_DELIM;
        ByteArrayOutputStream bas = new ByteArrayOutputStream();
        try {
            bas.write(StringUtils.uft8StringToBytes(header));
        } catch (IOException e) {
            throw new RuntimeException("Can't write header to byte array stream, header: " + header, e);
        }

        if (content != null) {
            try {
                bas.write(content);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write content to byte stream", e);
            }
        }
        return bas.toByteArray();
    }

    private void disableCachingOnClientSide() {
        if (headers == null) {
            headers = new LinkedHashMap<>();
        }

        setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        setHeader("Pragma", "no-cache");
        setHeader("Expires", "0");
    }

    private static String getStatusText(int status) {
        switch (status) {
            case 200:
                return "OK";
            case 101:
                return "Switching Protocols";
            case 301:
                return "Moved Permanently";
            case 302:
                return "Found";
            case 404:
                return "Not Found";
            case 500:
                return "Server Error";
            default:
                return "";
        }
    }

    @Override
    public String toString() {
        return "HttpResponse{status: " + status
                + ", content size: " + (content != null ? content.length : 0) + "}";
    }
}
