package com.github.netty.protocol.nrpc;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;

import static com.github.netty.core.util.IOUtil.BYTE_LENGTH;
import static com.github.netty.core.util.IOUtil.CHAR_LENGTH;
import static com.github.netty.protocol.nrpc.RpcEncoder.PROTOCOL_HEADER;
import static com.github.netty.protocol.nrpc.RpcEncoder.RPC_CHARSET;
import static com.github.netty.protocol.nrpc.RpcPacket.RequestPacket;
import static com.github.netty.protocol.nrpc.RpcPacket.ResponsePacket;

/**
 *  RPC decoder
 *
 *   Request Packet (note:  1 = request type)
 *-+------8B--------+--1B--+--1B--+------2B------+-----4B-----+------1B--------+-----length-----+------1B-------+---length----+-----2B------+-------length-------------+
 * | header/version | type | ACK   | total length | Request ID | service length | service name   | method length | method name | data length |         data             |
 * |   NRPC/010     |  1   | 1    |     55       |     1      |       8        | "/sys/user"    |      7        |  getUser    |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+----------------+----------------+---------------+-------------+-------------+--------------------------+
 *
 *
 *   Response Packet (note: 2 = response type)
 *-+------8B--------+--1B--+--1B--+------2B------+-----4B-----+---1B---+--------1B------+--length--+---1B---+-----2B------+----------length----------+
 * | header/version | type | ACK   | total length | Request ID | status | message length | message  | encode | data length |         data             |
 * |   NRPC/010     |  2   | 0    |     35       |     1      |  200   |       2        |  ok      | 1      |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+--------+----------------+----------+--------+-------------+--------------------------+
 *
 * @author wangzihao
 */
public class RpcDecoder extends LengthFieldBasedFrameDecoder {
   private static final byte[] EMPTY = new byte[0];

    public RpcDecoder() {
        this(10 * 1024 * 1024);
    }

    public RpcDecoder(int maxLength) {
        super(maxLength,
                //  header | type | ACK
                PROTOCOL_HEADER.length + BYTE_LENGTH + BYTE_LENGTH,
                CHAR_LENGTH,
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
        int totalLength = msg.readUnsignedShort();

        switch (rpcType){
            case RpcPacket.TYPE_REQUEST:{
                RequestPacket packet = RequestPacket.newInstance();
                //Ack
                packet.setAck(ack);

                //Request ID
                packet.setRequestId(msg.readInt());

                //Request service
                packet.setServiceName(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request method
                packet.setMethodName(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request data
                int dataLength = msg.readUnsignedShort();
                if(dataLength > 0) {
                    packet.setData(new byte[dataLength]);
                    msg.readBytes(packet.getData());
                }else {
                    packet.setData(EMPTY);
                }
                return packet;
            }
            case RpcPacket.TYPE_RESPONSE:{
                ResponsePacket packet = ResponsePacket.newInstance();
                //Ack
                packet.setAck(ack);

                //Request ID
                packet.setRequestId(msg.readInt());

                //Response status
                packet.setStatus((int) msg.readUnsignedByte());

                //Response encode
                packet.setEncode(DataCodec.Encode.indexOf(msg.readUnsignedByte()));

                //Response information
                packet.setMessage(msg.readCharSequence(msg.readUnsignedByte(), RPC_CHARSET).toString());

                //Request data
                int dataLength = msg.readUnsignedShort();
                if(dataLength > 0) {
                    packet.setData(new byte[dataLength]);
                    msg.readBytes(packet.getData());
                }else {
                    packet.setData(null);
                }
                return packet;
            }
            default:{
                RpcPacket packet = new RpcPacket(rpcType);
                //ack
                packet.setAck(ack);

                //data
                if(totalLength > 0) {
                    packet.setData(new byte[totalLength]);
                    msg.readBytes(packet.getData());
                }else {
                    packet.setData(null);
                }
                return packet;
            }
        }
    }

}
