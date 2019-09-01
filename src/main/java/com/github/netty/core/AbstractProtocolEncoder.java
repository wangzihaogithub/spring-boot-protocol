package com.github.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.AsciiString;

import java.util.Map;
import java.util.Objects;

import static com.github.netty.core.util.IOUtil.BYTE_LENGTH;
import static com.github.netty.core.util.IOUtil.CHAR_LENGTH;

/**
 * RPC encoder
 *
 *   ACK flag : (0=Don't need, 1=Need)
 *
 *-+------2B-------+--1B--+----1B----+-----8B-----+------1B-----+----------------dynamic---------------------+-------dynamic------------+
 * | packet length | type | ACK flag |   version  | Fields size |                Fields                      |          Body            |
 * |      76       |  1   |   1      |   NRPC/201 |     2       | 11requestMappingName6/hello10methodName8sayHello  | {"age":10,"name":"wang"} |
 *-+---------------+------+----------+------------+-------------+--------------------------------------------+--------------------------+
 *
 * @author wangzihao
 */
public class AbstractProtocolEncoder<T extends Packet> extends MessageToByteEncoder<T> {
    /**
     * Fixed length (note : Not including the total length.)
     * (versionBytes)B + (packet type)1B + (packet ack flag)1B
     */
    private int fixedLength;
    private byte[] versionBytes;

    public AbstractProtocolEncoder() {
        setVersionBytes(new byte[0]);
    }

    @Override
    public void encode(ChannelHandlerContext ctx, T packet, ByteBuf out) throws Exception {
        int packetLength = fixedLength;

        //(2 byte Unsigned) mak total length
        int writerTotalLengthIndex = out.writerIndex();
        out.writerIndex(writerTotalLengthIndex + CHAR_LENGTH);

        //(1 byte Unsigned) packet type
        out.writeByte(packet.getPacketType());

        //(1 byte) packet ack flag
        out.writeByte(packet.getAck());

        //(8 byte) protocol head
        out.writeBytes(versionBytes);

        //Fields
        Map<AsciiString, ByteBuf> fieldMap = packet.getFieldMap();
        int fieldSize = fieldMap == null ? 0 : fieldMap.size();
        //(1 byte Unsigned) Fields size
        out.writeByte(fieldSize);
        if (fieldSize > 0) {
            packetLength += fieldSize * 3;
            for (Map.Entry<AsciiString, ByteBuf> entry : fieldMap.entrySet()) {
                AsciiString key = entry.getKey();
                ByteBuf value = entry.getValue();

                //(key.length byte Unsigned) Fields size
                packetLength += key.length();
                out.writeByte(key.length());
                ByteBufUtil.writeAscii(out,key);

                //(value.length byte Unsigned) Fields size
                packetLength += value.readableBytes();
                out.writeChar(value.readableBytes());
                out.writeBytes(value);
            }
        }

        //Body
        ByteBuf body = packet.getBody();
        if (body.readableBytes() > 0) {
            packetLength += body.readableBytes();
            out.writeBytes(body);
        }

        //Fill total length
        out.setChar(writerTotalLengthIndex, packetLength);

        //retain
//        out.retain();
    }

    public void setVersionBytes(byte[] versionBytes) {
        this.versionBytes = Objects.requireNonNull(versionBytes);
        // versionBytesLength(length) + type(1B) + ACK flag(1B) + Fields size(1B)
        this.fixedLength = versionBytes.length + BYTE_LENGTH + BYTE_LENGTH + BYTE_LENGTH;
    }

    public byte[] getVersionBytes() {
        return versionBytes;
    }

}
