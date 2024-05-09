package com.github.netty.protocol.dubbo;

import io.netty.buffer.ByteBuf;

import static com.github.netty.protocol.dubbo.Constant.SERIALIZATION_MASK;

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