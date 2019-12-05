package com.github.netty.core;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TcpChannel {
    private static final Map<ChannelId,TcpChannel> channels = new ConcurrentHashMap<>();
    private Channel channel;
    private ProtocolHandler protocolHandler;

    public TcpChannel(Channel channel, ProtocolHandler protocolHandler) {
        this.channel = channel;
        this.protocolHandler = protocolHandler;
    }

    public static Map<ChannelId,TcpChannel> getChannels() {
        return channels;
    }

    public String getProtocolName(){
        return protocolHandler.getProtocolName();
    }

    public Channel getChannel() {
        return channel;
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }
}
