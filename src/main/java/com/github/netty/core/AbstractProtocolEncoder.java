package com.github.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
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
 *
 *-+------2B-------+--1B--+----1B----+------protocolHeaderLength----+-----dynamic-----+-------dynamic------------+
 * | packet length | type | ACK flag |           header             |      Fields     |          Body            |
 * |      55       |  1   |   1      |         MyProtocol           | 5mykey7myvalue  | {"age":10,"name":"wang"} |
 *-+---------------+------+----------+------------------------------+-----------------+--------------------------+
 *
 *
 * @author wangzihao
 */
@ChannelHandler.Sharable
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
        Map<AsciiString,AsciiString> fieldMap = packet.getFieldMap();
        //(1 byte Unsigned) Fields size
        int fieldSize = fieldMap.size();
        out.writeByte(fieldSize);
        int packetLength = fixedLength + fieldSize * 3;
        for(Map.Entry<AsciiString,AsciiString> entry : fieldMap.entrySet()){
            AsciiString key = entry.getKey();
            AsciiString value = entry.getValue();

            //(key.length byte Unsigned) Fields size
            out.writeByte(key.length());
            out.writeBytes(key.array(), key.arrayOffset(), key.length());
            packetLength += key.length();

            //(value.length byte Unsigned) Fields size
            out.writeChar(value.length());
            out.writeBytes(value.array(), value.arrayOffset(), value.length());
            packetLength += value.length();
        }
        fieldMap.clear();

        //Body
        byte[] body = packet.getBody();
        if(body.length > 0) {
            out.writeBytes(body);
            packetLength += body.length;
        }

        //Fill total length
        out.setChar(writerTotalLengthIndex,packetLength);
    }

    public void setVersionBytes(byte[] versionBytes) {
        this.versionBytes = Objects.requireNonNull(versionBytes);
        this.versionBytes = versionBytes;
        this.fixedLength = versionBytes.length + BYTE_LENGTH + BYTE_LENGTH;
    }

    public byte[] getVersionBytes() {
        return versionBytes;
    }

}
