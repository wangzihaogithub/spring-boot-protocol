package com.github.netty.protocol.mysql.client;

import com.github.netty.protocol.mysql.AbstractPacketDecoder;
import com.github.netty.protocol.mysql.CapabilityFlags;
import com.github.netty.protocol.mysql.CodecUtils;
import com.github.netty.protocol.mysql.MysqlCharacterSet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;

/**
 *
 */
public class ClientConnectionDecoder extends AbstractPacketDecoder implements ClientDecoder {

	public ClientConnectionDecoder() {
		this(DEFAULT_MAX_PACKET_SIZE);
	}

	public ClientConnectionDecoder(int maxPacketSize) {
		super(maxPacketSize);
	}

	@Override
	protected void decodePacket(ChannelHandlerContext ctx, int sequenceId, ByteBuf packet, List<Object> out) {
		final EnumSet<CapabilityFlags> clientCapabilities = CodecUtils.readIntEnumSet(packet, CapabilityFlags.class);

		if (!clientCapabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
			throw new DecoderException("MySQL client protocol 4.1 support required");
		}

		final ClientHandshakePacket.Builder response = ClientHandshakePacket.create();
		response.sequenceId(sequenceId);
		response.addCapabilities(clientCapabilities)
				.maxPacketSize((int)packet.readUnsignedIntLE());
		final MysqlCharacterSet characterSet = MysqlCharacterSet.findByIdIfNullDefault(packet.readByte());
		response.characterSet(characterSet);
		packet.skipBytes(23);
		if (packet.isReadable()) {
			response.username(CodecUtils.readNullTerminatedString(packet, characterSet.getCharset()));

			final EnumSet<CapabilityFlags> serverCapabilities = CapabilityFlags.getCapabilitiesAttr(ctx.channel());
			final EnumSet<CapabilityFlags> capabilities = EnumSet.copyOf(clientCapabilities);
			capabilities.retainAll(serverCapabilities);

			final int authResponseLength;
			if (capabilities.contains(CapabilityFlags.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA)) {
				authResponseLength = (int)CodecUtils.readLengthEncodedInteger(packet);
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
				response.authPluginName(CodecUtils.readNullTerminatedString(packet, StandardCharsets.UTF_8));
			}

			if (capabilities.contains(CapabilityFlags.CLIENT_CONNECT_ATTRS)) {
				final long keyValueLen = CodecUtils.readLengthEncodedInteger(packet);
				for (int i = 0; i < keyValueLen; i++) {
					response.addAttribute(
							CodecUtils.readLengthEncodedString(packet, StandardCharsets.UTF_8),
							CodecUtils.readLengthEncodedString(packet, StandardCharsets.UTF_8));
				}
			}
		}
		out.add(response.build());
	}
}
