package com.github.netty.protocol.servlet.util;

import com.github.netty.core.util.IOUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.HttpConstants;

import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;

public enum Protocol {

    http1_1(false),
    https1_1(false),
    h2(true),
    h2c(true),
    h2c_prior_knowledge(true);

    Protocol(boolean http2) {
        this.http2 = http2;
    }

    private static final ByteBuf CONNECTION_PREFACE = unreleasableBuffer(connectionPrefaceBuf()).asReadOnly();

    private final boolean http2;

    public boolean isHttp2() {
        return http2;
    }

    public static boolean isHttpPacket(ByteBuf packet) {
        int protocolEndIndex = IOUtil.indexOf(packet, HttpConstants.LF);
        if (protocolEndIndex == -1 && packet.readableBytes() > 7) {
            // client multiple write packages. cause browser out of length.
            if (packet.getByte(0) == 'G'
                    && packet.getByte(1) == 'E'
                    && packet.getByte(2) == 'T'
                    && packet.getByte(3) == ' '
                    && packet.getByte(4) == '/') {
                return true;
            } else if (packet.getByte(0) == 'P'
                    && packet.getByte(1) == 'O'
                    && packet.getByte(2) == 'S'
                    && packet.getByte(3) == 'T'
                    && packet.getByte(4) == ' '
                    && packet.getByte(5) == '/') {
                return true;
            } else if (packet.getByte(0) == 'P'
                    && packet.getByte(1) == 'U'
                    && packet.getByte(2) == 'T'
                    && packet.getByte(3) == ' '
                    && packet.getByte(4) == '/') {
                return true;
            } else if (packet.getByte(0) == 'D'
                    && packet.getByte(1) == 'E'
                    && packet.getByte(2) == 'L'
                    && packet.getByte(3) == 'E'
                    && packet.getByte(4) == 'T'
                    && packet.getByte(5) == 'E'
                    && packet.getByte(6) == ' '
                    && packet.getByte(7) == '/') {
                return true;
            } else if (packet.getByte(0) == 'P'
                    && packet.getByte(1) == 'A'
                    && packet.getByte(2) == 'T'
                    && packet.getByte(3) == 'C'
                    && packet.getByte(4) == 'H'
                    && packet.getByte(5) == ' '
                    && packet.getByte(6) == '/') {
                return true;
            } else {
                return false;
            }
        } else if (protocolEndIndex < 9) {
            return false;
        } else if (packet.getByte(protocolEndIndex - 9) == 'H'
                && packet.getByte(protocolEndIndex - 8) == 'T'
                && packet.getByte(protocolEndIndex - 7) == 'T'
                && packet.getByte(protocolEndIndex - 6) == 'P') {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isPriHttp2(ByteBuf clientFirstMsg) {
        int prefaceLength = CONNECTION_PREFACE.readableBytes();
        int bytesRead = Math.min(clientFirstMsg.readableBytes(), prefaceLength);
        return ByteBufUtil.equals(CONNECTION_PREFACE, CONNECTION_PREFACE.readerIndex(),
                clientFirstMsg, clientFirstMsg.readerIndex(), bytesRead);
    }

}