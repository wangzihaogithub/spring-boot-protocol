package com.github.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelId;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * tcp channel
 *
 * @author wangzihao
 */
public class TcpChannel {
    private static final Map<ChannelId, TcpChannel> CHANNELS = new ConcurrentHashMap<>(32);
    private final Channel channel;
    private final ChannelHandler channelHandler;
    private ProtocolHandler protocol;

    public TcpChannel(Channel channel, ChannelHandler channelHandler) {
        this.channel = channel;
        this.channelHandler = channelHandler;
    }

    public static Map<ChannelId, TcpChannel> getChannels() {
        return CHANNELS;
    }

    public String getProtocolName() {
        return protocol == null ? null : protocol.getProtocolName();
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelHandler getChannelHandler() {
        return channelHandler;
    }

    public ProtocolHandler getProtocol() {
        return protocol;
    }

    public void setProtocol(ProtocolHandler protocol) {
        this.protocol = protocol;
    }

    public boolean isActive() {
        return channel.isActive();
    }

    public ChannelFuture writeAndFlush(byte[] msg) {
        return channel.writeAndFlush(Unpooled.wrappedBuffer(msg));
    }

    public ChannelFuture writeAndFlush(String msg, Charset charset) {
        return channel.writeAndFlush(Unpooled.copiedBuffer(msg, charset));
    }

    public ChannelFuture writeAndFlush(ByteBuf byteBuf) {
        return channel.writeAndFlush(byteBuf);
    }

    public ChannelFuture close() {
        return channel.close();
    }

    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel.attr(key);
    }

    @Override
    public String toString() {
        return Objects.toString(protocol, "null") + channel;
    }
}
