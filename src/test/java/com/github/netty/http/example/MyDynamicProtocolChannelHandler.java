package com.github.netty.http.example;

import com.github.netty.core.ProtocolHandler;
import com.github.netty.protocol.DynamicProtocolChannelHandler;
import com.github.netty.core.TcpChannel;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import org.springframework.stereotype.Component;

/**
 * Users can expand their own. This is all the agreements before entering entrance
 * @author wangzihao
 */
@Component
public class MyDynamicProtocolChannelHandler extends DynamicProtocolChannelHandler {

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf clientFirstMsg) throws Exception {
        super.onMessageReceived(ctx, clientFirstMsg);
    }

    @Override
    protected void addPipeline(ChannelHandlerContext ctx, ProtocolHandler protocolHandler, ByteBuf clientFirstMsg) throws Exception {
        super.addPipeline(ctx, protocolHandler, clientFirstMsg);
    }

    @Override
    public ProtocolHandler getProtocolHandler(ByteBuf clientFirstMsg) {
        return super.getProtocolHandler(clientFirstMsg);
    }

    @Override
    public ProtocolHandler getProtocolHandler(Channel channel) {
        return super.getProtocolHandler(channel);
    }

    @Override
    protected boolean onOutOfMaxConnection(ChannelHandlerContext ctx, ByteBuf clientFirstMsg, TcpChannel tcpChannel, int currentConnections, int maxConnections) {
        return super.onOutOfMaxConnection(ctx, clientFirstMsg, tcpChannel, currentConnections, maxConnections);
    }

    @Override
    protected void onProtocolBindTimeout(ChannelHandlerContext ctx, TcpChannel tcpChannel) {
        super.onProtocolBindTimeout(ctx, tcpChannel);
    }

    @Override
    protected void onNoSupportProtocol(ChannelHandlerContext ctx, ByteBuf clientFirstMsg) {
        super.onNoSupportProtocol(ctx, clientFirstMsg);
    }

    @Override
    public TcpChannel getConnection(ChannelId id) {
        return super.getConnection(id);
    }

    @Override
    public void addConnection(ChannelId id, TcpChannel tcpChannel) {
        super.addConnection(id, tcpChannel);
    }

    @Override
    public void removeConnection(ChannelId id) {
        super.removeConnection(id);
    }

    @Override
    public int getConnectionCount() {
        return super.getConnectionCount();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}
