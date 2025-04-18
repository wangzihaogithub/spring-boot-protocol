package com.github.netty.protocol.dubbo;

import io.netty.buffer.ByteBuf;

import static com.github.netty.protocol.dubbo.Constant.*;

public class Header {
    final ByteBuf headerBytes;
    // request and serialization flag.
    final byte flag;
    final byte status;
    final long requestId;
    // 8 - 1-request/0-response
    final byte type;
    final int bodyLength;

    public Header(ByteBuf headerBytes, byte flag, byte status, long requestId, byte type, int bodyLength) {
        this.headerBytes = headerBytes;
        this.flag = flag;
        this.status = status;
        this.requestId = requestId;
        this.type = type;
        this.bodyLength = bodyLength;
    }

    public boolean isEvent() {
        return (flag & FLAG_EVENT) != 0;
    }

    public boolean isTwoway() {
        return (flag & FLAG_TWOWAY) != 0;
    }

    public boolean isRequest() {
        return (flag & FLAG_REQUEST) != 0;
    }

    public byte getFlag() {
        return flag;
    }

    public byte getStatus() {
        return status;
    }

    public long getRequestId() {
        return requestId;
    }

    public byte getType() {
        return type;
    }

    public int getBodyLength() {
        return bodyLength;
    }

    public byte getSerializationProtoId() {
        return (byte) (flag & SERIALIZATION_MASK);
    }

    public ByteBuf encode() {
        return headerBytes;
    }

    public boolean release() {
        if (headerBytes != null && headerBytes.refCnt() > 0) {
            return headerBytes.release();
        } else {
            return false;
        }
    }

    public static Header readHeader(ByteBuf buffer) {
        int readerIndex = buffer.readerIndex();

        // request and serialization flag. -62 isHeartBeat
        byte flag = buffer.getByte(readerIndex + 2);
        byte status = buffer.getByte(readerIndex + 3);
        long requestId = buffer.getLong(readerIndex + 4);
        // 8 - 1-request/0-response
        byte type = buffer.getByte(readerIndex + 8);
        int bodyLength = buffer.getInt(readerIndex + 12);

        ByteBuf headerBytes = buffer.readRetainedSlice(HEADER_LENGTH);
        return new Header(headerBytes, flag, status, requestId, type, bodyLength);
    }

    @Override
    public String toString() {
        return "Header{" +
                "\n\trequestId=" + requestId +
                ",\n\tserialization=" + getSerializationProtoId() +
                ",\n\tstatus=" + Constant.statusToString(status) +
                ",\n\tbodyLength=" + bodyLength +
                "\n}";
    }
}