package com.github.netty.core;

import com.github.netty.core.util.AsciiStringCachePool;
import com.github.netty.core.util.RecyclableUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.AsciiString;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  AbstractProtocolDecoder
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
public abstract class AbstractProtocolDecoder extends LengthFieldBasedFrameDecoder {
    private int protocolVersionLength;
//    private static long cumulationOffset;
//    private static final Unsafe UNSAFE = IOUtil.getUnsafe();

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
//        setCumulator(COMPOSITE_CUMULATOR);
    }


    @Override
    protected ByteBuf extractFrame(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
        return buffer.copy(index,length);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        ByteBuf msg = (ByteBuf) super.decode(ctx, buffer);
        if(msg == null){
            return null;
        }

        boolean release = true;
        Packet packet = null;
        try {
            //Packet type
            packet = newPacket(msg.readUnsignedByte());

            if(Packet.isDebugPacket()) {
                Packet.Debug debug = packet.getDebug();
                debug.setInstancePacket(msg.toString(Charset.defaultCharset()));
                debug.setInstanceThread(Thread.currentThread());
            }

            packet.setRawPacket(msg);

            //Ack flag
            packet.setAck(msg.readByte());

            //Protocol header
            packet.setProtocolVersion(msg.readSlice(protocolVersionLength));

            //Fields
            int fieldCount = msg.readUnsignedByte();
            Map<AsciiString, ByteBuf> fieldMap = packet.getFieldMap();
            if (fieldMap == null && fieldCount != 0) {
                fieldMap = new ConcurrentHashMap<>(fieldCount);
                packet.setFieldMap(fieldMap);
            }
            for (int i = 0; i < fieldCount; i++) {
                int keyLen = msg.readUnsignedByte();
                AsciiString key = AsciiStringCachePool.newInstance(
                        ByteBufUtil.getBytes(msg, msg.readerIndex(), keyLen, false));
                msg.skipBytes(keyLen);
                ByteBuf value = msg.readSlice(msg.readUnsignedShort());
                fieldMap.put(key,value);
            }

            //Body
            int bodyLength = msg.readableBytes();
            if (bodyLength > 0) {
                packet.setBody(msg.readSlice(bodyLength));
            } else {
                packet.setBody(Unpooled.EMPTY_BUFFER);
            }
            release = false;
            return packet;
        }finally {
            if(release){
                RecyclableUtil.release(msg);
                RecyclableUtil.release(packet);
            }
        }
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

//    public void setCumulation(ByteBuf cumulation) {
//        UNSAFE.putObject(this,cumulationOffset,cumulation);
//    }


//    static {
//        try {
//            cumulationOffset = UNSAFE.objectFieldOffset
//                    (ByteToMessageDecoder.class.getDeclaredField("cumulation"));
//        } catch (Exception ex) {
//            throw new Error(ex);
//        }
//    }

}
