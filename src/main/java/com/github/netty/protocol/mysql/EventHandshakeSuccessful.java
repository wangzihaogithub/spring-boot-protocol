package com.github.netty.protocol.mysql;

import com.github.netty.protocol.mysql.server.ServerHandshakePacket;
import com.github.netty.protocol.mysql.server.ServerOkPacket;

public class EventHandshakeSuccessful {
    private final long timestamp = System.currentTimeMillis();
    private ServerHandshakePacket handshakePacket;
    private ServerOkPacket serverOkPacket;

    public EventHandshakeSuccessful(ServerHandshakePacket handshakePacket, ServerOkPacket serverOkPacket) {
        this.handshakePacket = handshakePacket;
        this.serverOkPacket = serverOkPacket;
    }

    public ServerHandshakePacket getHandshakePacket() {
        return handshakePacket;
    }

    public ServerOkPacket getServerOkPacket() {
        return serverOkPacket;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
