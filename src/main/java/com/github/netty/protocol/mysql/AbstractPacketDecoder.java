package com.github.netty.protocol.mysql;

import com.github.netty.protocol.mysql.server.ServerEofPacket;
import com.github.netty.protocol.mysql.server.ServerErrorPacket;
import com.github.netty.protocol.mysql.server.ServerOkPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 *
 */
public abstract class AbstractPacketDecoder extends ByteToMessageDecoder implements Constants {
    private final int maxPacketSize;

    public AbstractPacketDecoder(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.isReadable(4)) {
            in.markReaderIndex();
            int packetSize = in.readUnsignedMediumLE();
            if (packetSize > maxPacketSize) {
                throw new TooLongFrameException("Received a packet of size " + packetSize + " but the maximum packet size is " + maxPacketSize);
            }
            int sequenceId = in.readByte();
            if (!in.isReadable(packetSize)) {
                in.resetReaderIndex();
                return;
            }
            ByteBuf packet = in.readSlice(packetSize);

            decodePacket(ctx, sequenceId, packet, out);
        }
    }

    protected abstract void decodePacket(ChannelHandlerContext ctx, int sequenceId, ByteBuf packet, List<Object> out);

    protected ServerOkPacket decodeOkResponse(int sequenceId, ByteBuf packet, Set<CapabilityFlags> capabilities,
                                              MysqlCharacterSet charset) {

        ServerOkPacket.Builder builder = ServerOkPacket.builder()
                .sequenceId(sequenceId)
                .affectedRows(CodecUtils.readLengthEncodedInteger(packet))
                .lastInsertId(CodecUtils.readLengthEncodedInteger(packet));

        EnumSet<ServerStatusFlag> statusFlags = CodecUtils.readShortEnumSet(packet, ServerStatusFlag.class);
        if (capabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
            builder
                    .addStatusFlags(statusFlags)
                    .warnings(packet.readUnsignedShortLE());
        } else if (capabilities.contains(CapabilityFlags.CLIENT_TRANSACTIONS)) {
            builder.addStatusFlags(statusFlags);
        }

        if (capabilities.contains(CapabilityFlags.CLIENT_SESSION_TRACK)) {
            builder.info(CodecUtils.readLengthEncodedString(packet, charset.getCharset()));
            if (statusFlags.contains(ServerStatusFlag.SESSION_STATE_CHANGED)) {
                builder.sessionStateChanges(CodecUtils.readLengthEncodedString(packet, charset.getCharset()));
            }
        } else {
            builder.info(CodecUtils.readFixedLengthString(packet, packet.readableBytes(), charset.getCharset()));
        }
        return builder.build();
    }

    protected ServerEofPacket decodeEofResponse(int sequenceId, ByteBuf packet, Set<CapabilityFlags> capabilities) {
        if (capabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
            return new ServerEofPacket(
                    sequenceId,
                    packet.readUnsignedShortLE(),
                    CodecUtils.readShortEnumSet(packet, ServerStatusFlag.class));
        } else {
            return new ServerEofPacket(sequenceId, 0);
        }
    }

    protected ServerErrorPacket decodeErrorResponse(int sequenceId, ByteBuf packet, MysqlCharacterSet charset) {
        int errorNumber = packet.readUnsignedShortLE();

        byte[] sqlState;
        sqlState = new byte[SQL_STATE_SIZE];
        packet.readBytes(sqlState);

        String message = CodecUtils.readFixedLengthString(packet, packet.readableBytes(), charset.getCharset());

        return new ServerErrorPacket(sequenceId, errorNumber, sqlState, message);
    }

}