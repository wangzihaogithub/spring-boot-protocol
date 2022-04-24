package com.github.netty.protocol.nrpc.codec;

import com.github.netty.protocol.nrpc.RpcPacket;
import com.github.netty.protocol.nrpc.RpcVersion;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.Charset;

import static com.github.netty.core.util.IOUtil.*;
import static com.github.netty.protocol.nrpc.RpcPacket.*;

/**
 * RPC encoder
 * <p>
 * <p>
 * Request Packet (note:  1 = request type)
 * -+------8B--------+--1B--+--1B--+------4B------+-----4B-----+-----4B-----+------1B--------+-----length-----+------1B-------+---length----+-----4B------+-------length-------------+
 * | header/version | type | ACK   | total length | Request ID| timeout/ms | service length | service name   | method length | method name | data length |         data             |
 * |   NRPC/010     |  1   | 1    |     55       |     1      |     1000   |       8        | "/sys/user"    |      7        |  getUser    |     24      | {"age":10,"name":"wang"} |
 * -+----------------+------+------+--------------+-----------+------------+----------------+----------------+---------------+-------------+-------------+--------------------------+
 * <p>
 * <p>
 * Response Packet (note: 2 = response type)
 * -+------8B--------+--1B--+--1B--+------4B------+-----4B-----+---2B---+--------1B------+--length--+---1B---+-----4B------+----------length----------+
 * | header/version | type | ACK   | total length | Request ID | status | message length | message  | encode | data length |         data             |
 * |   NRPC/010     |  2   | 0    |     35       |     1      |  200   |       2        |  ok      | 1      |     24      | {"age":10,"name":"wang"} |
 * -+----------------+------+------+--------------+------------+--------+----------------+----------+--------+-------------+--------------------------+
 *
 * @author wangzihao
 */
@ChannelHandler.Sharable
public class RpcEncoder extends MessageToByteEncoder<RpcPacket> {
    /**
     * protocol header
     * Fixed 8 length
     */
    public static final byte[] PROTOCOL_HEADER = RpcVersion.CURRENT_VERSION.getTextBytes();
    public static final Charset RPC_CHARSET = Charset.forName("UTF-8");
    /**
     * Fixed request length (note : Not including the total length.)
     * (Request ID)4B + (timeout/ms)4B + (service name length)1B + (service version length)1B + (method length)1B + (data length)4B
     */
    private static final int FIXED_REQUEST_LENGTH = INT_LENGTH + INT_LENGTH + BYTE_LENGTH + BYTE_LENGTH + BYTE_LENGTH + INT_LENGTH;
    /**
     * Fixed response length (note : Not including the total length.)
     * (Request ID)4B + (status)2B + (message length)1B + (encode)1B + (data length)4B
     */
    private static final int FIXED_RESPONSE_LENGTH = INT_LENGTH + SHORT_LENGTH + BYTE_LENGTH + BYTE_LENGTH + INT_LENGTH;

    public RpcEncoder() {
    }

    @Override
    public void encode(ChannelHandlerContext ctx, RpcPacket packet, ByteBuf out) throws Exception {
        int packetType = packet.getPacketType();
        try {
            switch (packetType) {
                case TYPE_CLIENT_REQUEST: {
                    encodePacket((RequestPacket) packet, out);
                    break;
                }
                case TYPE_RESPONSE_CHUNK_ACK:
                case TYPE_RESPONSE_CHUNK:
                case TYPE_RESPONSE_LAST: {
                    encodePacket((ResponsePacket) packet, out);
                    break;
                }
                default: {
                    //(8 byte) protocol head
                    out.writeBytes(PROTOCOL_HEADER);

                    //(1 byte Unsigned) RPC packet type
                    out.writeByte(packet.getPacketType());

                    //(1 byte Unsigned) RPC packet ack
                    out.writeByte(packet.getAck());

                    //(4 byte Unsigned) data length
                    byte[] data = packet.getData();
                    int writeTotalLength = data == null ? 0 : data.length;

                    //(4 byte Unsigned) total length
                    out.writeInt(writeTotalLength);
                    if (writeTotalLength > 0) {
                        out.writeBytes(data);
                    }
                }
            }
        } finally {
            packet.recycle();
        }
    }

    protected void encodePacket(RequestPacket packet, ByteBuf out) {
        int writeCurrentLength;
        int writeTotalLength = FIXED_REQUEST_LENGTH;

        //(8 byte) protocol head
        out.writeBytes(PROTOCOL_HEADER);

        //(1 byte Unsigned) RPC packet type
        out.writeByte(RpcPacket.TYPE_CLIENT_REQUEST);

        //(1 byte Unsigned) RPC packet ack
        out.writeByte(packet.getAck());

        //(4 byte Unsigned) total length
        int writerTotalLengthIndex = out.writerIndex();
        out.writerIndex(writerTotalLengthIndex + INT_LENGTH);

        //(4 byte) Request ID
        out.writeInt(packet.getRequestId());

        //(4 byte) Request Timeout
        out.writeInt(packet.getTimeout());

        //(length byte) service name
        out.writerIndex(out.writerIndex() + BYTE_LENGTH);
        writeCurrentLength = out.writeCharSequence(packet.getRequestMappingName(), RPC_CHARSET);

        //(1 byte Unsigned) service name length
        out.setByte(out.writerIndex() - writeCurrentLength - BYTE_LENGTH, writeCurrentLength);
        writeTotalLength += writeCurrentLength;

        //(length byte) service version
        out.writerIndex(out.writerIndex() + BYTE_LENGTH);
        writeCurrentLength = out.writeCharSequence(packet.getVersion(), RPC_CHARSET);

        //(1 byte Unsigned) service version length
        out.setByte(out.writerIndex() - writeCurrentLength - BYTE_LENGTH, writeCurrentLength);
        writeTotalLength += writeCurrentLength;

        //(length byte Unsigned)  method name
        out.writerIndex(out.writerIndex() + BYTE_LENGTH);
        writeCurrentLength = out.writeCharSequence(packet.getMethodName(), RPC_CHARSET);

        //(1 byte Unsigned) method length
        out.setByte(out.writerIndex() - writeCurrentLength - BYTE_LENGTH, writeCurrentLength);
        writeTotalLength += writeCurrentLength;

        //(4 byte Unsigned) data length
        byte[] data = packet.getData();
        out.writeInt(data.length);
        if (data.length > 0) {
            //(length byte)  data
            out.writeBytes(data);
            writeTotalLength += data.length;
        }

        //set total length Unsigned
        out.setInt(writerTotalLengthIndex, writeTotalLength);
    }

    protected void encodePacket(ResponsePacket packet, ByteBuf out) {
        int writeCurrentLength;
        int writeTotalLength = FIXED_RESPONSE_LENGTH;

        //(8 byte) protocol head
        out.writeBytes(PROTOCOL_HEADER);

        //(1 byte Unsigned) RPC packet type
        out.writeByte(packet.getPacketType());

        //(1 byte Unsigned) RPC packet ack
        out.writeByte(packet.getAck());

        //(4 byte Unsigned) total length
        int writerTotalLengthIndex = out.writerIndex();
        out.writerIndex(writerTotalLengthIndex + INT_LENGTH);

        //(4 byte) Request ID
        out.writeInt(packet.getRequestId());

        //(2 byte Unsigned) Response status
        out.writeShort(packet.getStatus());

        //(1 byte Unsigned) Whether the data has been encoded
        out.writeByte(packet.getEncode().getCode());

        //(length byte) Response information
        out.writerIndex(out.writerIndex() + BYTE_LENGTH);
        writeCurrentLength = out.writeCharSequence(packet.getMessage(), RPC_CHARSET);

        //(1 byte Unsigned) Response information length
        out.setByte(out.writerIndex() - writeCurrentLength - BYTE_LENGTH, writeCurrentLength);
        writeTotalLength += writeCurrentLength;

        //(4 byte Unsigned) data length
        byte[] data = packet.getData();
        writeCurrentLength = data == null ? 0 : data.length;
        out.writeInt(writeCurrentLength);
        if (writeCurrentLength > 0) {
            out.writeBytes(data);
            //(length byte)  data
            writeTotalLength += writeCurrentLength;
        }

        if (packet instanceof ResponseChunkPacket) {
            // (2 byte Unsigned)  chunk id
            writeTotalLength += SHORT_LENGTH;
            out.writeShort(((ResponseChunkPacket) packet).getChunkId());
        } else if (packet instanceof ResponseChunkAckPacket) {
            // (2 byte Unsigned)  ack chunk id
            writeTotalLength += SHORT_LENGTH;
            out.writeShort(((ResponseChunkAckPacket) packet).getAckChunkId());
        }

        //set total length
        out.setInt(writerTotalLengthIndex, writeTotalLength);
    }

}
