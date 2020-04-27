package com.github.netty.protocol.nrpc;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;

import static com.github.netty.core.util.IOUtil.BYTE_LENGTH;
import static com.github.netty.core.util.IOUtil.INT_LENGTH;
import static com.github.netty.protocol.nrpc.RpcEncoder.PROTOCOL_HEADER;
import static com.github.netty.protocol.nrpc.RpcEncoder.RPC_CHARSET;
import static com.github.netty.protocol.nrpc.RpcPacket.RequestPacket;
import static com.github.netty.protocol.nrpc.RpcPacket.ResponsePacket;

/**
 *  RPC decoder
 *
 *   Request Packet (note:  1 = request type)
 *-+------8B--------+--1B--+--1B--+------4B------+-----4B-----+------1B--------+-----length-----+------1B-------+---length----+-----4B------+-------length-------------+
 * | header/version | type | ACK   | total length | Request ID | service length | service name   | method length | method name | data length |         data             |
 * |   NRPC/010     |  1   | 1    |     55       |     1      |       8        | "/sys/user"    |      7        |  getUser    |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+----------------+----------------+---------------+-------------+-------------+--------------------------+
 *
 *
 *   Response Packet (note: 2 = response type)
 *-+------8B--------+--1B--+--1B--+------4B------+-----4B-----+---2B---+--------1B------+--length--+---1B---+-----4B------+----------length----------+
 * | header/version | type | ACK   | total length | Request ID | status | message length | message  | encode | data length |         data             |
 * |   NRPC/010     |  2   | 0    |     35       |     1      |  200   |       2        |  ok      | 1      |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+--------+----------------+----------+--------+-------------+--------------------------+
 *
 * @author wangzihao
 */
public class RpcDecoder extends LengthFieldBasedFrameDecoder {
   private static final byte[] EMPTY = {};
   private static final int LENGTH_FIELD_OFFSET = PROTOCOL_HEADER.length + BYTE_LENGTH + BYTE_LENGTH;

    public RpcDecoder() {
        this(10 * 1024 * 1024);
    }

    public RpcDecoder(int maxLength) {
        super(maxLength,
                //  header | type | ACK
                LENGTH_FIELD_OFFSET,
                INT_LENGTH,
                0,
                0,
                true);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        ByteBuf msg = (ByteBuf) super.decode(ctx, buffer);
        if(msg == null){
            return null;
        }

        try {
            return decodeToPojo(msg);
        }finally {
            if(msg.refCnt() > 0) {
                ReferenceCountUtil.safeRelease(msg);
            }
        }
    }

    /**
     * Resolve to the entity class
     * @param msg msg
     * @return
     */
    protected Object decodeToPojo(ByteBuf msg){
        //Skip protocol header
        msg.skipBytes(PROTOCOL_HEADER.length);

        byte rpcType = msg.readByte();
        byte ack = msg.readByte();

        //read total length
        long totalLength = msg.readUnsignedInt();
        long totalPacketLength = totalLength + LENGTH_FIELD_OFFSET;

        switch (rpcType){
            case RpcPacket.TYPE_REQUEST:{
                RequestPacket packet = RequestPacket.newInstance();
                packet.setPacketLength(totalPacketLength);
                //Ack
                packet.setAck(ack);

                //Request ID
                packet.setRequestId(msg.readInt());

                //Request service name
                packet.setRequestMappingName(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request service version
                packet.setVersion(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request method
                packet.setMethodName(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request data
                long dataLength = msg.readUnsignedInt();
                if(dataLength > 0) {
                    packet.setData(new byte[(int) dataLength]);
                    msg.readBytes(packet.getData());
                }else {
                    packet.setData(EMPTY);
                }
                return packet;
            }
            case RpcPacket.TYPE_RESPONSE:{
                ResponsePacket packet = ResponsePacket.newInstance();
                packet.setPacketLength(totalPacketLength);
                //Ack
                packet.setAck(ack);

                //Request ID
                packet.setRequestId(msg.readInt());

                //Response status
                packet.setStatus(msg.readUnsignedShort());

                //Response encode
                packet.setEncode(DataCodec.Encode.indexOf(msg.readUnsignedByte()));

                //Response information
                packet.setMessage(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request data
                long dataLength = msg.readUnsignedInt();
                if(dataLength > 0) {
                    packet.setData(new byte[(int) dataLength]);
                    msg.readBytes(packet.getData());
                }else {
                    packet.setData(null);
                }
                return packet;
            }
            default:{
                RpcPacket packet = new RpcPacket(rpcType);
                packet.setPacketLength(totalPacketLength);
                //ack
                packet.setAck(ack);

                //data
                if(totalLength > 0) {
                    packet.setData(new byte[(int) totalLength]);
                    msg.readBytes(packet.getData());
                }else {
                    packet.setData(null);
                }
                return packet;
            }
        }
    }

}
