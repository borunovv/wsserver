package com.borunovv.util;

import com.borunovv.contract.Precondition;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public final class NIOUtils {

    public static String tryGetRemoteIpAddress(SocketChannel client) {
        try {
            return getIpAddressAsString(client.getRemoteAddress());
        } catch (IOException ignore) {
            return "";
        }
    }

    public static int tryGetRemotePort(SocketChannel client) {
        int port = 0;
        try {
            if (client.getRemoteAddress() instanceof InetSocketAddress) {
                InetSocketAddress inetAddress = (InetSocketAddress) client.getRemoteAddress();
                port = inetAddress.getPort();
            }
        } catch (IOException ignore) {
        }

        return port;
    }


    private static String getIpAddressAsString(SocketAddress address) {
        Precondition.expected(address instanceof InetSocketAddress,
                "address must be of type InetSocketAddress. Actual type is: " + address.getClass().getSimpleName());

        InetSocketAddress inetAddress = (InetSocketAddress) address;
        return inetAddress.getAddress().getHostAddress();
    }
}