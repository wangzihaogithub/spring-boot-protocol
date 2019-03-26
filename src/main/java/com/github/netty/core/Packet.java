package com.github.netty.core;

import com.github.netty.core.util.RecyclableUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCounted;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Packet
 * 2019/3/17/017.
 * @author acer01
 */
public class Packet implements ReferenceCounted {
    public static final byte TYPE_UNKNOWN = 0;
    public static final byte TYPE_REQUEST = 1;
    public static final byte TYPE_RESPONSE = 2;
    public static final byte TYPE_PING = 3;
    public static final byte TYPE_PONG = 4;

    public static final byte ACK_NO = 0;
    public static final byte ACK_YES = 1;

    private ByteBuf rawPacket;
    /**
     * Protocol version
     */
    private ByteBuf protocolVersion;
    /**
     * Packet type
     */
    private int packetType = TYPE_UNKNOWN;
    /**
     * 1 = Need ack
     * 0 = Don't need ack
     */
    private byte ack = ACK_NO;
    /**
     * Fields
     */
    private Map<ByteBuf, ByteBuf> fieldMap;
    /**
     * Body
     */
    private ByteBuf body = Unpooled.EMPTY_BUFFER;

    public Packet() {}

    public Packet(int packetType) {
        this.packetType = packetType;
    }

    public ByteBuf getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(ByteBuf protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public int getAck() {
        return ack;
    }

    public void setAck(byte ack) {
        this.ack = ack;
    }

    public int getPacketType() {
        return packetType;
    }

    public void setPacketType(int packetType) {
        this.packetType = packetType;
    }

    public ByteBuf getBody() {
        return body;
    }

    public void setBody(ByteBuf body) {
        this.body = body;
    }

    public Map<ByteBuf, ByteBuf> getFieldMap() {
        return fieldMap;
    }

    public void setFieldMap(Map<ByteBuf, ByteBuf> fieldMap) {
        this.fieldMap = fieldMap;
    }

    public ByteBuf getRawPacket() {
        return rawPacket;
    }

    public void setRawPacket(ByteBuf rawPacket) {
        this.rawPacket = rawPacket;
    }

    @Override
    public String toString() {
        Charset charset = Charset.defaultCharset();
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("{protocolVersion=");
        sb.append(protocolVersion == null? "null" : protocolVersion.toString(charset));
        sb.append(", packetType=");
        sb.append(packetType);
        sb.append(", ack=");
        sb.append(ack);
        sb.append(", bodyLength=");
        sb.append(body == null ? "null" : body.readableBytes());
        sb.append(", fieldMap=");

        if(fieldMap == null){
            sb.append("null");
        }else {
            Iterator<Map.Entry<ByteBuf,ByteBuf>> i = fieldMap.entrySet().iterator();
            if (i.hasNext()) {
                sb.append('{');
                for (;;) {
                    Map.Entry<ByteBuf,ByteBuf> e = i.next();
                    sb.append(e.getKey().toString(charset));
                    sb.append('=');
                    sb.append(e.getValue().toString(charset));
                    if (! i.hasNext()) {
                        sb.append('}');
                        break;
                    }
                    sb.append(',').append(' ');
                }
            }else {
                sb.append("{}");
            }
            sb.append('}');
        }
        return sb.toString();
    }


    private AtomicInteger refCnt = new AtomicInteger(1);

    @Override
    public final int refCnt() {
        return refCnt.get();
    }

    @Override
    public final ReferenceCounted retain() {
        return retain(1);
    }

    @Override
    public final ReferenceCounted retain(int increment) {
        refCnt.addAndGet(increment);
        return this;
    }

    @Override
    public final ReferenceCounted touch() {
        return this;
    }

    @Override
    public final ReferenceCounted touch(Object hint) {
        return this;
    }

    @Override
    public final boolean release() {
        return release(1);
    }

    @Override
    public final boolean release(int decrement) {
        synchronized (this) {
            int oldRef = refCnt.getAndAdd(-decrement);
            if (oldRef == decrement) {
                deallocate();
                return true;
            } else if (oldRef < decrement || oldRef - decrement > oldRef) {
                // Ensure we don't over-release, and avoid underflow.
                refCnt.getAndAdd(decrement);
                throw new IllegalReferenceCountException(oldRef, -decrement);
            }
        }
        return false;
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected void deallocate(){
        RecyclableUtil.release(rawPacket);
        RecyclableUtil.release(protocolVersion);
        if (fieldMap != null && fieldMap.size() > 0) {
            for (Map.Entry<ByteBuf, ByteBuf> entry : fieldMap.entrySet()) {
                RecyclableUtil.release(entry.getKey());
                RecyclableUtil.release(entry.getValue());
            }
        }
        RecyclableUtil.release(body);
    }
}