package com.borunovv.wsserver.protocol.websocket.client;

import com.borunovv.log.Log;
import com.borunovv.util.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

/**
 * @author borunovv
 */
public class WSClient implements Closeable {
    private Socket socket;
    private byte[] buffer = new byte[1024 * 10];
    private String host;
    private int port;

    public WSClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.host = host;
        this.port = port;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    public String doHandShake() throws IOException {
        String handshake = makeHandshake(host, port, "example.com");
        byte[] data = StringUtils.uft8StringToBytes(handshake);

        Log.trace("WSClient: -> Send:\n" + handshake);
        write(data);

        byte[] responseData = read();
        String response = StringUtils.toUtf8String(responseData);
        Log.trace("WSClient: <- Receive:\n" + response);

        return response;
    }

    private void write(byte[] data) throws IOException {
        socket.getOutputStream().write(data);
    }

    private byte[] read() throws IOException {
        int red = socket.getInputStream().read(buffer);
        if (red >= 0) {
            return Arrays.copyOfRange(buffer, 0, red);
        }
        return null;
    }

    private String makeHandshake(String host, int port, String originHost) {
        String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String keyBase64 = "6oT4v/eAbo7hqEoUHLpNgA==";

        String request = ""
                + "GET ws://" + host + ":" + port + "/ HTTP/1.1\r\n" +
                "Host: " + host + ":" + port + "\r\n" +
                "Connection: Upgrade\r\n" +
                "Pragma: no-cache\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Upgrade: websocket\r\n" +
                "Origin: " + originHost + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "User-Agent: The best agent ever\r\n" +
                "Accept-Encoding: gzip, deflate\r\n" +
                "Accept-Language: ru-RU,ru;q=0.8,en-US;q=0.6,en;q=0.4\r\n" +
                "Sec-WebSocket-Key: " + keyBase64 + "\r\n" +
                "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits\r\n" +
                "\r\n";

        return request;
    }
}
