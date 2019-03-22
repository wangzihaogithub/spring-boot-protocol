package com.github.netty.core;

import com.github.netty.core.util.AsciiStringMap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;

/**
 *  AbstractProtocolDecoder
 *
 *   ACK flag : (0=Don't need, 1=Need)
 *
 *-+------2B-------+--1B--+----1B----+---versionLength-+-----dynamic-----+-------dynamic------------+
 * | packet length | type | ACK flag |    version      |      Fields     |          Body            |
 * |      45       |  1   |   1      |     my007       | 5mykey7myvalue  | {"age":10,"name":"wang"} |
 *-+---------------+------+----------+-----------------+-----------------+--------------------------+
 *
 * @author wangzihao
 */
public abstract class AbstractProtocolDecoder extends LengthFieldBasedFrameDecoder {
    private final int protocolVersionLength;

    public AbstractProtocolDecoder(int protocolVersionLength) {
        this(protocolVersionLength,10 * 1024 * 1024);
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

        try {
            //Packet type
            Packet packet = newPacket(msg.readUnsignedByte());

            //Ack flag
            packet.setAck(msg.readByte());

            //Protocol header
            packet.setProtocolVersion(new byte[protocolVersionLength]);
            msg.readBytes(packet.getProtocolVersion());

            //Fields
            int fieldCount = msg.readUnsignedByte();
            AsciiStringMap fieldMap = new AsciiStringMap(fieldCount);
            for(int i=0; i<fieldCount; i++){
                byte[] key = new byte[msg.readUnsignedByte()];
                msg.readBytes(key);

                byte[] value = new byte[msg.readUnsignedShort()];
                msg.readBytes(value);

                fieldMap.put(new AsciiString(key,false),new AsciiString(value,false));
            }
            packet.setFieldMap(fieldMap);

            //Body
            int bodyLength = msg.readableBytes();
            if(bodyLength > 0){
                packet.setBody(new byte[bodyLength]);
                msg.readBytes(packet.getBody());
            }else {
                packet.setBody(Packet.EMPTY_BODY);
            }
            return packet;
        }finally {
            if(msg.refCnt() > 0) {
                ReferenceCountUtil.safeRelease(msg);
            }
        }
    }

    /**
     * new packet
     * @param packetType
     * @return
     */
    protected Packet newPacket(int packetType) {
        return new Packet(packetType);
    }

}
