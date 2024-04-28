package com.github.netty.protocol.dubbo;

import io.netty.buffer.ByteBuf;

public abstract class Body {
    ByteBuf bodyBytes;

    public ByteBuf bytes() {
        return bodyBytes;
    }
}
