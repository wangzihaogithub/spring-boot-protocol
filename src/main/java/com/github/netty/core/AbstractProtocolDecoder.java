package com.github.netty.core;

import com.github.netty.core.util.FixedArrayMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;

import java.util.Map;

/**
 *  AbstractProtocolDecoder
 *
 *   ACK flag : (0=Don't need, 1=Need)
 *
 *-+------2B-------+--1B--+----1B----+-----8B-----+------1B-----+----------------dynamic---------------------+-------dynamic------------+
 * | packet length | type | ACK flag |   version  | Fields size |                Fields                      |          Body            |
 * |      76       |  1   |   1      |   NRPC/201 |     2       | 11serviceName6/hello10methodName8sayHello  | {"age":10,"name":"wang"} |
 *-+---------------+------+----------+------------+-------------+--------------------------------------------+--------------------------+
 *
 * @author wangzihao
 */
public abstract class AbstractProtocolDecoder extends LengthFieldBasedFrameDecoder {
    private int protocolVersionLength;

    public AbstractProtocolDecoder() {
        this(0,10 * 1024 * 1024);
    }

    public AbstractProtocolDecoder(int maxLength) {
        this(0,maxLength);
    }

    public AbstractProtocolDecoder(int protocolVersionLength, int maxLength) {
        super(maxLength,
                0,
                2,
                0,
                2,
                true);
        this.protocolVersionLength = protocolVersionLength;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        ByteBuf msg = (ByteBuf) super.decode(ctx, buffer);
        if(msg == null){
            return null;
        }

        //Packet type
        Packet packet = newPacket(msg.readUnsignedByte());
        packet.setRawPacket(msg);

        //Ack flag
        packet.setAck(msg.readByte());

        //Protocol header
        packet.setProtocolVersion(msg.readSlice(protocolVersionLength));

        //Fields
        int fieldCount = msg.readUnsignedByte();
        Map<ByteBuf,ByteBuf> fieldMap = packet.getFieldMap();
        if(fieldMap == null) {
            fieldMap = new FixedArrayMap<>(fieldCount);
        }
        for(int i=0; i<fieldCount; i++){
            fieldMap.put(
                    msg.readSlice(msg.readUnsignedByte()),
                    msg.readSlice(msg.readUnsignedShort()));
        }
        packet.setFieldMap(fieldMap);

        //Body
        int bodyLength = msg.readableBytes();
        if(bodyLength > 0){
            packet.setBody(msg.readSlice(bodyLength));
        }else {
            packet.setBody(Unpooled.EMPTY_BUFFER);
        }
        return packet;
    }

    /**
     * new packet
     * @param packetType packetType
     * @return Packet
     */
    protected Packet newPacket(int packetType) {
        return new Packet(packetType);
    }

    public void setProtocolVersionLength(int protocolVersionLength) {
        this.protocolVersionLength = protocolVersionLength;
    }
}
