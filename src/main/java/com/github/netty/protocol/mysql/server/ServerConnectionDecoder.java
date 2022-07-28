package com.github.netty.protocol.mysql.server;

import com.github.netty.protocol.mysql.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CodecException;
import io.netty.util.CharsetUtil;

import java.util.EnumSet;
import java.util.List;

/**
 *
 */
public class ServerConnectionDecoder extends AbstractPacketDecoder implements ServerDecoder {
    private Session session;

    public ServerConnectionDecoder(Session session, int maxPacketSize) {
        super(maxPacketSize);
        this.session = session;
    }

    @Override
    protected void decodePacket(ChannelHandlerContext ctx, int sequenceId, ByteBuf packet, List<Object> out) {
        EnumSet<CapabilityFlags> capabilities = session.getFrontendCapabilities();
        MysqlCharacterSet serverCharset = session.getServerCharset();

        int header = packet.readUnsignedByte();
        switch (header) {
            case RESPONSE_OK:
                out.add(decodeOkResponse(sequenceId, packet, capabilities, serverCharset));
                break;
            case RESPONSE_EOF:
                if (capabilities.contains(CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
                    decodeAuthSwitchRequest(sequenceId, packet, out);
                } else {
                    out.add(decodeEofResponse(sequenceId, packet, capabilities));
                }
                break;
            case RESPONSE_ERROR:
                out.add(decodeErrorResponse(sequenceId, packet, serverCharset));
                break;
            case 1:
                // TODO Decode auth more data packet: https://dev.mysql.com/doc/internals/en/connection-phase-packets.html#packet-Protocol::AuthMoreData
                throw new UnsupportedOperationException("Implement auth more data");
            default:
                decodeHandshake(ctx, packet, sequenceId, out, header);
        }
    }

    private void decodeAuthSwitchRequest(int sequenceId, ByteBuf packet, List<Object> out) {
        // TODO Implement AuthSwitchRequest decode
        throw new UnsupportedOperationException("Implement decodeAuthSwitchRequest decode.");
    }

    private void decodeHandshake(ChannelHandlerContext ctx, ByteBuf packet, int sequenceId, List<Object> out, int protocolVersion) {
        if (protocolVersion < MINIMUM_SUPPORTED_PROTOCOL_VERSION) {
            throw new CodecException("Unsupported version of MySQL");
        }

        ServerHandshakePacket.Builder builder = ServerHandshakePacket.builder();
        builder.sequenceId(sequenceId)
                .protocolVersion(protocolVersion)
                .serverVersion(CodecUtils.readNullTerminatedString(packet))
                .connectionId(packet.readIntLE())
                .addAuthData(packet, Constants.AUTH_PLUGIN_DATA_PART1_LEN);

        packet.skipBytes(1); // Skip auth plugin data terminator
        builder.addCapabilities(CodecUtils.toEnumSet(CapabilityFlags.class, packet.readUnsignedShortLE()));
        if (packet.isReadable()) {
            MysqlCharacterSet characterSet = MysqlCharacterSet.findById(packet.readByte());

            builder.characterSet(characterSet)
                    .addServerStatus(CodecUtils.readShortEnumSet(packet, ServerStatusFlag.class))
                    .addCapabilities(
                            CodecUtils.toEnumSet(CapabilityFlags.class, packet.readUnsignedShortLE() << Short.SIZE));
            if (builder.hasCapability(CapabilityFlags.CLIENT_SECURE_CONNECTION)) {
                int authDataLen = packet.readByte();

                packet.skipBytes(Constants.HANDSHAKE_RESERVED_BYTES); // Skip reserved bytes
                int readableBytes =
                        Math.max(Constants.AUTH_PLUGIN_DATA_PART2_MIN_LEN,
                                authDataLen - Constants.AUTH_PLUGIN_DATA_PART1_LEN);
                builder.addAuthData(packet, readableBytes);
                if (builder.hasCapability(CapabilityFlags.CLIENT_PLUGIN_AUTH) && packet.isReadable()) {
                    int len = packet.readableBytes();
                    if (packet.getByte(packet.readerIndex() + len - 1) == 0) {
                        len--;
                    }
                    builder.authPluginName(CodecUtils.readFixedLengthString(packet, len, CharsetUtil.UTF_8));
                    packet.skipBytes(1);
                }
            }
        }
        ServerHandshakePacket handshake = builder.build();
        out.add(handshake);
    }
}
