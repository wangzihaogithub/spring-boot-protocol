package com.github.netty.protocol.mysql.listener;

import com.github.netty.protocol.mysql.MysqlPacket;
import com.github.netty.protocol.mysql.Session;
import io.netty.channel.ChannelHandlerContext;

@FunctionalInterface
public interface MysqlPacketListener {

    void onMysqlPacket(MysqlPacket packet,
                       ChannelHandlerContext currentContext,
                       Session session,
                       String handlerType);
}
