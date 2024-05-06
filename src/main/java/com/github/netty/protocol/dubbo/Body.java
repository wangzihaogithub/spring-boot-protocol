package com.github.netty.protocol.dubbo;

import io.netty.buffer.ByteBuf;

public abstract class Body {
    ByteBuf bodyBytes;

    public ByteBuf bytes() {
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
