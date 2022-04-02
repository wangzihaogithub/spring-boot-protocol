package com.github.netty.springmyprotocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;

public class MyClient {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", 8080));

        socket.getOutputStream().write(SpringMyProtocolBootstrap.MyProtocol.HANDSHAKE_KEY.getBytes(Charset.forName("utf-8")));
        socket.getOutputStream().flush();

        byte[] serverMsg = new byte[4096];
        socket.getInputStream().read(serverMsg);
        System.out.println("read = " + new String(serverMsg));

        for (int i = 0; i < 100; i++) {
            String msg = "你好啊" + i + "先生.";
            socket.getOutputStream().write(msg.getBytes(Charset.forName("utf-8")));
            socket.getOutputStream().flush();
        }
        socket.getOutputStream().write("拜拜~".getBytes(Charset.forName("utf-8")));
        socket.close();
    }
}
