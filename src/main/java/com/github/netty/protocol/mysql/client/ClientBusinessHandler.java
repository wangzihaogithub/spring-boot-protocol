package com.github.netty.protocol.mysql.client;

import com.github.netty.protocol.mysql.*;
import com.github.netty.protocol.mysql.client.*;
import com.github.netty.protocol.mysql.server.ServerPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class ClientBusinessHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(!(msg instanceof MysqlPacket)){
           return;
        }
        if (msg instanceof ClientHandshakePacket) {
            ctx.pipeline().replace(ClientConnectionDecoder.class,
                    "CommandPacketDecoder", new ClientCommandDecoder());
            return;
        }

        if(msg instanceof ClientPacket) {
            ClientPacket clientPacket = (ClientPacket) msg;
            if (clientPacket instanceof ClientQueryPacket) {
                System.out.println("packet = " + clientPacket);
            } else if (clientPacket instanceof ClientCommandPacket) {
                System.out.println("packet = " + clientPacket);
            }
        }else if(msg instanceof ServerPacket){
            System.out.println("packet = " + msg);
        }
    }


}
