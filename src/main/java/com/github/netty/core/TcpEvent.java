package com.github.netty.core;

import io.netty.channel.Channel;

public class TcpEvent {
    public static final int EVENT_CONNECTION_REFUSED = 1;
    private final int event;
    private final Channel channel;
    private final long timestamp = System.currentTimeMillis();
    public TcpEvent(int event, Channel channel) {
        this.event = event;
        this.channel = channel;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getEvent() {
        return event;
    }

    public Channel getChannel() {
        return channel;
    }
}
