package com.github.netty.mysql.example;

import com.github.netty.protocol.mysql.EventHandshakeSuccessful;
import com.github.netty.protocol.mysql.server.MysqlBackendBusinessHandler;
import com.github.netty.protocol.mysql.server.ServerHandshakePacket;
import com.github.netty.protocol.mysql.server.ServerPacket;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Scope("prototype")
@Component
public class MysqlBackendHandler extends MysqlBackendBusinessHandler {
    @Override
    protected void onMysqlPacket(ChannelHandlerContext ctx, ServerPacket packet) {
        //
    }

    @Override
    protected void onHandshakeSuccessful(ChannelHandlerContext ctx, EventHandshakeSuccessful event) {
        super.onHandshakeSuccessful(ctx, event);
    }

    @Override
    protected void onHandshake(ChannelHandlerContext ctx, ServerHandshakePacket packet) {
        super.onHandshake(ctx, packet);
    }
}
