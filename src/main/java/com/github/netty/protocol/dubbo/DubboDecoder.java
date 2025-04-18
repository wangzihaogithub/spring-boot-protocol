package com.github.netty.protocol.dubbo;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import static com.github.netty.protocol.dubbo.Constant.*;

public class DubboDecoder extends ByteToMessageDecoder {
    private State state = State.READ_HEADER;
    private DubboPacket packet;

    public static boolean isDubboProtocol(ByteBuf buffer) {
        int readerIndex = buffer.readerIndex();
        return buffer.readableBytes() >= 2
                && buffer.getByte(readerIndex) == MAGIC_0
                && buffer.getByte(readerIndex + 1) == MAGIC_1;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        boolean hasNext;
        do {
            switch (this.state) {
                case READ_HEADER: {
                    if (buffer.readableBytes() >= HEADER_LENGTH) {
                        try {
                            this.packet = new DubboPacket(Header.readHeader(buffer));
                        } catch (Exception e) {
                            exception(ctx, buffer, e);
                            throw e;
                        }
                        this.state = State.READ_BODY;
                        hasNext = buffer.isReadable();
                    } else {
                        hasNext = false;
                    }
                    break;
                }
                case READ_BODY: {
                    if (buffer.readableBytes() >= this.packet.header.bodyLength) {
                        ByteBuf body = buffer.readRetainedSlice(this.packet.header.bodyLength);
                        int markReaderIndex = body.readerIndex();
                        try {
                            this.packet.body = Body.readBody(body, packet.header);
                        } catch (Exception e) {
                            exception(ctx, buffer, e);
                            this.packet.release();
                            throw e;
                        }
                        this.packet.body.markReaderIndex = markReaderIndex;
                        this.packet.body.bodyBytes = body;
                        this.state = State.READ_HEADER;
                        out.add(this.packet);
                        this.packet = null;
                        hasNext = buffer.isReadable();
                    } else {
                        hasNext = false;
                    }
                    break;
                }
                default: {
                    hasNext = false;
                    break;
                }
            }
        } while (hasNext);
    }

    protected <E extends Exception> void exception(ChannelHandlerContext ctx, ByteBuf buffer, E cause) throws Exception {
        buffer.release();
        ctx.close();
    }

    protected enum State {
        READ_HEADER, READ_BODY
    }
}