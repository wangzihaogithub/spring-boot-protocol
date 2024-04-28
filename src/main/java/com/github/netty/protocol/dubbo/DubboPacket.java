package com.github.netty.protocol.dubbo;

import io.netty.buffer.ByteBuf;

public class DubboPacket {
    final Header header;
    Body body;

    public DubboPacket(Header header) {
        this.header = header;
    }

    public Header getHeader() {
        return header;
    }

    public Body getBody() {
        return body;
    }

    public ByteBuf getHeaderBytes() {
        return header.bytes();
    }

    public ByteBuf getBodyBytes() {
        return body.bytes();
    }
}