package com.github.netty.protocol.mysql.client;

import com.github.netty.protocol.mysql.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;

import java.util.List;

/**
 *
 */
public class ClientCommandDecoder extends AbstractPacketDecoder implements ClientDecoder {
    private Session session;

    public ClientCommandDecoder(Session session, int maxPacketSize) {
        super(maxPacketSize);
        this.session = session;
    }

    @Override
    protected void decodePacket(ChannelHandlerContext ctx, int sequenceId, ByteBuf packet, List<Object> out) {
        MysqlCharacterSet clientCharset = session.getClientCharset();

        byte commandCode = packet.readByte();
        Command command = Command.findByCommandCode(commandCode);
        if (command == null) {
            throw new DecoderException("Unknown command " + commandCode);
        }
        switch (command) {
            case COM_QUERY:
                out.add(new ClientQueryPacket(sequenceId, CodecUtils.readFixedLengthString(packet, packet.readableBytes(), clientCharset.getCharset())));
                break;
            default:
                out.add(new ClientCommandPacket(sequenceId, command));
        }
    }
}
