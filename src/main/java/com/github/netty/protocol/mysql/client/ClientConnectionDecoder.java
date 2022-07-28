package com.github.netty.protocol.mysql.client;

import com.github.netty.protocol.mysql.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;

import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.List;

/**
 * @author Administrator
 */
public class ClientConnectionDecoder extends AbstractPacketDecoder implements ClientDecoder {
    private Session session;

    public ClientConnectionDecoder(Session session, int maxPacketSize) {
        super(maxPacketSize);
        this.session = session;
    }

    @Override
    protected void decodePacket(ChannelHandlerContext ctx, int sequenceId, ByteBuf packet, List<Object> out) {
        EnumSet<CapabilityFlags> clientCapabilities = CodecUtils.readIntEnumSet(packet, CapabilityFlags.class);

        if (!clientCapabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
            throw new DecoderException("MySQL client protocol 4.1 support required");
        }

        ClientHandshakePacket.Builder response = ClientHandshakePacket.create();
        response.sequenceId(sequenceId);
        response.addCapabilities(clientCapabilities)
                .maxPacketSize((int) packet.readUnsignedIntLE());
        MysqlCharacterSet characterSet = MysqlCharacterSet.findById(packet.readUnsignedByte());

        response.characterSet(characterSet);
        packet.skipBytes(23);
        if (packet.isReadable()) {
            response.username(CodecUtils.readNullTerminatedString(packet, characterSet.getCharset()));

            EnumSet<CapabilityFlags> serverCapabilities = session.getBackendCapabilities();
            EnumSet<CapabilityFlags> capabilities = EnumSet.copyOf(clientCapabilities);
            capabilities.retainAll(serverCapabilities);

            int authResponseLength;
            if (capabilities.contains(CapabilityFlags.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA)) {
                authResponseLength = (int) CodecUtils.readLengthEncodedInteger(packet);
            } else if (capabilities.contains(CapabilityFlags.CLIENT_SECURE_CONNECTION)) {
                authResponseLength = packet.readUnsignedByte();
            } else {
                authResponseLength = CodecUtils.findNullTermLen(packet);
            }
            response.addAuthData(packet, authResponseLength);

            if (capabilities.contains(CapabilityFlags.CLIENT_CONNECT_WITH_DB)) {
                response.database(CodecUtils.readNullTerminatedString(packet, characterSet.getCharset()));
            }

            if (capabilities.contains(CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
                response.authPluginName(CodecUtils.readNullTerminatedString(packet, Charset.forName("UTF-8")));
            }

            if (capabilities.contains(CapabilityFlags.CLIENT_CONNECT_ATTRS)) {
                long keyValueLen = CodecUtils.readLengthEncodedInteger(packet);
                int readIndex = packet.readerIndex();
                long endIndex = readIndex + keyValueLen;
                while (packet.readerIndex() < endIndex) {
                    response.addAttribute(
                            CodecUtils.readLengthEncodedString(packet, Charset.forName("UTF-8")),
                            CodecUtils.readLengthEncodedString(packet, Charset.forName("UTF-8")));
                }
            }
        }
        out.add(response.build());
    }
}
