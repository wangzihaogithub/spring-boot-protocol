package com.github.netty.protocol.servlet.util;

import com.github.netty.core.util.IOUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpConstants;

import java.nio.charset.Charset;

public enum Protocol {
    /**/
    http1_1(false),
    https1_1(false),
    h2(true),
    h2c(true),
    h2c_prior_knowledge(true);

    private static final ByteBuf CONNECTION_PREFACE = Unpooled.unreleasableBuffer(Unpooled.directBuffer(24).writeBytes("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(Charset.forName("UTF-8")))).asReadOnly();
    private final boolean http2;

    Protocol(boolean http2) {
        this.http2 = http2;
    }

    public static boolean isHttpPacket(ByteBuf packet) {
        int protocolEndIndex = IOUtil.indexOf(packet, HttpConstants.LF);
        if (protocolEndIndex == -1 && packet.readableBytes() > 7) {
            int readerIndex = packet.readerIndex();
            // client multiple write packages. cause browser out of length.
            if (packet.getByte(readerIndex) == 'G'
                    && packet.getByte(readerIndex + 1) == 'E'
                    && packet.getByte(readerIndex + 2) == 'T'
                    && packet.getByte(readerIndex + 3) == ' '
                    && packet.getByte(readerIndex + 4) == '/') {
                return true;
            } else if (packet.getByte(readerIndex) == 'P'
                    && packet.getByte(readerIndex + 1) == 'O'
                    && packet.getByte(readerIndex + 2) == 'S'
                    && packet.getByte(readerIndex + 3) == 'T'
                    && packet.getByte(readerIndex + 4) == ' '
                    && packet.getByte(readerIndex + 5) == '/') {
                return true;
            } else if (packet.getByte(readerIndex) == 'O'
                    && packet.getByte(readerIndex + 1) == 'P'
                    && packet.getByte(readerIndex + 2) == 'T'
                    && packet.getByte(readerIndex + 3) == 'I'
                    && packet.getByte(readerIndex + 4) == 'O'
                    && packet.getByte(readerIndex + 5) == 'N'
                    && packet.getByte(readerIndex + 6) == 'S'
                    && packet.getByte(readerIndex + 7) == ' '
                    && packet.getByte(readerIndex + 8) == '/') {
                return true;
            } else if (packet.getByte(readerIndex) == 'P'
                    && packet.getByte(readerIndex + 1) == 'U'
                    && packet.getByte(readerIndex + 2) == 'T'
                    && packet.getByte(readerIndex + 3) == ' '
                    && packet.getByte(readerIndex + 4) == '/') {
                return true;
            } else if (packet.getByte(readerIndex) == 'D'
                    && packet.getByte(readerIndex + 1) == 'E'
                    && packet.getByte(readerIndex + 2) == 'L'
                    && packet.getByte(readerIndex + 3) == 'E'
                    && packet.getByte(readerIndex + 4) == 'T'
                    && packet.getByte(readerIndex + 5) == 'E'
                    && packet.getByte(readerIndex + 6) == ' '
                    && packet.getByte(readerIndex + 7) == '/') {
                return true;
            } else {
                return packet.getByte(readerIndex) == 'P'
                        && packet.getByte(readerIndex + 1) == 'A'
                        && packet.getByte(readerIndex + 2) == 'T'
                        && packet.getByte(readerIndex + 3) == 'C'
                        && packet.getByte(readerIndex + 4) == 'H'
                        && packet.getByte(readerIndex + 5) == ' '
                        && packet.getByte(readerIndex + 6) == '/';
            }
        } else if (protocolEndIndex < 9) {
            return false;
        } else {
            return packet.getByte(protocolEndIndex - 9) == 'H'
                    && packet.getByte(protocolEndIndex - 8) == 'T'
                    && packet.getByte(protocolEndIndex - 7) == 'T'
                    && packet.getByte(protocolEndIndex - 6) == 'P';
        }
    }

    public static boolean isPriHttp2(ByteBuf clientFirstMsg) {
        int prefaceLength = CONNECTION_PREFACE.readableBytes();
        int bytesRead = Math.min(clientFirstMsg.readableBytes(), prefaceLength);
        return ByteBufUtil.equals(CONNECTION_PREFACE, CONNECTION_PREFACE.readerIndex(),
                clientFirstMsg, clientFirstMsg.readerIndex(), bytesRead);
    }

    public boolean isHttp2() {
        return http2;
    }

}