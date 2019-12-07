package com.github.netty.protocol;

import com.github.netty.core.ProtocolHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * tcp channel
 * @author wangzihao
 */
public class TcpChannel {
    private static final Map<ChannelId,TcpChannel> CHANNELS = new ConcurrentHashMap<>(32);
    private Channel channel;
    private ProtocolHandler protocol;
    private DynamicProtocolChannelHandler channelHandler;

    public TcpChannel(Channel channel, ProtocolHandler protocol,DynamicProtocolChannelHandler channelHandler) {
        this.channel = channel;
        this.protocol = protocol;
        this.channelHandler = channelHandler;
    }

    public static Map<ChannelId,TcpChannel> getChannels() {
        return CHANNELS;
    }

    public String getProtocolName(){
        return protocol.getProtocolName();
    }

    public Channel getChannel() {
        return channel;
    }

    public DynamicProtocolChannelHandler getChannelHandler() {
        return channelHandler;
    }

    public ProtocolHandler getProtocol() {
        return protocol;
    }

    public ChannelFuture writeAndFlush(byte[] msg){
        return channel.writeAndFlush(Unpooled.wrappedBuffer(msg));
    }

    public ChannelFuture writeAndFlush(String msg, Charset charset){
        return channel.writeAndFlush(Unpooled.copiedBuffer(msg,charset));
    }

    public ChannelFuture writeAndFlush(ByteBuf byteBuf){
        return channel.writeAndFlush(byteBuf);
    }

    public ChannelFuture close(){
        return channel.close();
    }

    public SocketAddress remoteAddress(){
        return channel.remoteAddress();
    }

    public <T> Attribute<T> attr(AttributeKey<T> key){
        return channel.attr(key);
    }

    @Override
    public String toString() {
        return protocol.toString() + channel;
    }
}
