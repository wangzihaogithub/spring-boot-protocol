package com.github.netty.core;

import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.core.util.SystemPropertyUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCounted;

import java.nio.charset.Charset;
import java.util.Arrays;
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
    private Map<AsciiString, ByteBuf> fieldMap;
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

    public Map<AsciiString, ByteBuf> getFieldMap() {
        return fieldMap;
    }

    public void setFieldMap(Map<AsciiString, ByteBuf> fieldMap) {
        this.fieldMap = fieldMap;
    }

    public ByteBuf getRawPacket() {
        return rawPacket;
    }

    public void setRawPacket(ByteBuf rawPacket) {
        this.rawPacket = rawPacket;
    }

    public ByteBuf putField(AsciiString key, ByteBuf value){
        boolean release = true;
        try {
            Map<AsciiString,ByteBuf> fieldMap = getFieldMap();
            if(fieldMap == null){
                throw new NullPointerException("fieldMap is null. put key = "+key);
            }
            ByteBuf old = fieldMap.put(key,value);
            release = false;
            return old;
        }finally {
            if(release) {
                RecyclableUtil.release(value);
            }
        }
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
            Iterator<Map.Entry<AsciiString,ByteBuf>> i = fieldMap.entrySet().iterator();
            if (i.hasNext()) {
                sb.append('{');
                for (;;) {
                    Map.Entry<AsciiString,ByteBuf> e = i.next();
                    sb.append(e.getKey());
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

    protected AtomicInteger getRefCntAtomic() {
        return refCnt;
    }

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
        int oldRef = refCnt.getAndAdd(-decrement);
        if (oldRef == decrement) {
            deallocate();
            return true;
        } else if (oldRef < decrement || oldRef - decrement > oldRef) {
            // Ensure we don't over-release, and avoid underflow.
            refCnt.getAndAdd(decrement);
            throw new IllegalReferenceCountException(oldRef, -decrement);
        }else {
            return false;
        }
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected void deallocate(){
        RecyclableUtil.release(rawPacket);
        RecyclableUtil.release(protocolVersion);
        if (fieldMap != null && fieldMap.size() > 0) {
            for (Map.Entry<AsciiString, ByteBuf> entry : fieldMap.entrySet()) {
                RecyclableUtil.release(entry.getValue());
            }
            fieldMap.clear();
        }
        RecyclableUtil.release(body);
        this.rawPacket = null;
        this.protocolVersion = null;
        this.body = null;

        if(debug != null) {
            debug.releaseStackTrace = new Throwable().getStackTrace();
            debug.releaseThread = Thread.currentThread();
        }
    }

    public Debug getDebug() {
        if(debugPacket && debug == null) {
            debug = new Debug();
        }
        return debug;
    }

    private Debug debug;
    public static boolean isDebugPacket() {
        return debugPacket;
    }

    private static boolean debugPacket = SystemPropertyUtil.getBoolean("netty-core.debugPacket",false);

    protected static class Debug{
        private volatile Thread instanceThread;
        private volatile String instancePacket;
        private volatile Thread releaseThread;
        private volatile StackTraceElement[] releaseStackTrace;

        public StackTraceElement[] getReleaseStackTrace() {
            return releaseStackTrace;
        }

        public String getInstancePacket() {
            return instancePacket;
        }

        public Thread getInstanceThread() {
            return instanceThread;
        }

        public Thread getReleaseThread() {
            return releaseThread;
        }

        public void setInstancePacket(String instancePacket) {
            this.instancePacket = instancePacket;
        }

        public void setInstanceThread(Thread instanceThread) {
            this.instanceThread = instanceThread;
        }

        public void setReleaseStackTrace(StackTraceElement[] releaseStackTrace) {
            this.releaseStackTrace = releaseStackTrace;
        }

        public void setReleaseThread(Thread releaseThread) {
            this.releaseThread = releaseThread;
        }

        @Override
        public String toString() {
            return "Debug{" +
                    "instanceThread=" + instanceThread +
                    ", instancePacket='" + instancePacket + '\'' +
                    ", releaseThread=" + releaseThread +
                    ", releaseStackTrace=" + Arrays.toString(releaseStackTrace) +
                    '}';
        }
    }


}