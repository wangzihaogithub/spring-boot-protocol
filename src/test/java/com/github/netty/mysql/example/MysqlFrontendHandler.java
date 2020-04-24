package com.github.netty.mysql.example;

import com.github.netty.protocol.mysql.EventHandshakeSuccessful;
import com.github.netty.protocol.mysql.client.ClientHandshakePacket;
import com.github.netty.protocol.mysql.client.ClientPacket;
import com.github.netty.protocol.mysql.client.MysqlFrontendBusinessHandler;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Scope("prototype")
@Component
public class MysqlFrontendHandler extends MysqlFrontendBusinessHandler {
    @Override
    protected void onMysqlPacket(ChannelHandlerContext ctx, ClientPacket packet) {
        //
    }

    @Override
    protected void onHandshakeSuccessful(ChannelHandlerContext ctx, EventHandshakeSuccessful event) {
        super.onHandshakeSuccessful(ctx, event);
    }

    @Override
    protected void onHandshake(ChannelHandlerContext ctx, ClientHandshakePacket packet) {
        super.onHandshake(ctx, packet);
    }
}