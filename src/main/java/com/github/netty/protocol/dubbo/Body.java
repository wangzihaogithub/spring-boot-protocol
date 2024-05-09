package com.github.netty.protocol.dubbo;

import io.netty.buffer.ByteBuf;

public abstract class Body {
    ByteBuf bodyBytes;
    int markReaderIndex;

    public ByteBuf encode() {
        if (bodyBytes != null) {
            bodyBytes.readerIndex(markReaderIndex);
        }
        return bodyBytes;
    }

    public boolean release() {
        if (bodyBytes != null && bodyBytes.refCnt() > 0) {
            return bodyBytes.release();
        } else {
            return false;
        }
    }
}
