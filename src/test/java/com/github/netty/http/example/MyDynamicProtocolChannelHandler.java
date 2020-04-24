package com.github.netty.http.example;

import com.github.netty.core.util.IOUtil;
import com.github.netty.protocol.DynamicProtocolChannelHandler;
import com.github.netty.protocol.TcpChannel;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelPromise;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;

/**
 * Users can expand their own. This is all the agreements before entering entrance
 * @author wangzihao
 */
@Component
public class MyDynamicProtocolChannelHandler extends DynamicProtocolChannelHandler {

    @Override
    protected void onOutOfMaxConnection(ChannelHandlerContext ctx, ByteBuf msg, TcpChannel tcpChannel) {
        if(tcpChannel.getProtocolName().contains("http")){
            onOutOfMaxConnectionByHttp(tcpChannel);
        }else {
            ctx.close();
        }
        if(msg != null && msg.refCnt() > 0) {
            msg.release();
        }
    }

    private void onOutOfMaxConnectionByHttp(TcpChannel tcpChannel){
        String body = "{\"name\":\"Denial-of-Service\"}";
        byte[] bodyBytes = body.getBytes(Charset.forName("UTF-8"));

        String head = "HTTP/1.1 503\r\n" +
                "Retry-After: 60\r\n" +
                "Content-Length: "+bodyBytes.length+"\r\n" +
                "Content-Type: application/json;charset=utf-8\r\n"+
                "\r\n";
        byte[] headBytes = head.getBytes(Charset.forName("ISO-8859-1"));

        tcpChannel.writeAndFlush(IOUtil.merge(headBytes,bodyBytes))
                .addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void onNoSupportProtocol(ChannelHandlerContext ctx, ByteBuf msg) {
        super.onNoSupportProtocol(ctx, msg);
    }

    @Override
    protected void addTcpChannel(ChannelId id, TcpChannel tcpChannel) {
        super.addTcpChannel(id, tcpChannel);
    }

    @Override
    protected void removeTcpChannel(ChannelId id) {
        super.removeTcpChannel(id);
    }

    @Override
    protected int getTcpChannelCount() {
        return super.getTcpChannelCount();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        super.onMessageReceived(ctx, msg);
    }

    @Override
    protected void onMessageWriter(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.onMessageWriter(ctx, msg, promise);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

}
