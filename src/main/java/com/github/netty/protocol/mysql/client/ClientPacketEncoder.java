/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.github.netty.protocol.mysql.client;

import com.github.netty.protocol.mysql.AbstractPacketEncoder;
import com.github.netty.protocol.mysql.CapabilityFlags;
import com.github.netty.protocol.mysql.CodecUtils;
import com.github.netty.protocol.mysql.MysqlCharacterSet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.Charset;
import java.util.Set;

/**
 *
 */
public class ClientPacketEncoder extends AbstractPacketEncoder<ClientPacket> {

	@Override
	protected void encodePacket(ChannelHandlerContext ctx, ClientPacket packet, ByteBuf buf) throws Exception {
		final Charset charset = MysqlCharacterSet.getClientCharsetAttr(ctx.channel()).getCharset();
		final Set<CapabilityFlags> capabilities = CapabilityFlags.getCapabilitiesAttr(ctx.channel());
		if (packet instanceof ClientCommandPacket) {
			encodeCommandPacket((ClientCommandPacket) packet, buf, charset);
		} else if (packet instanceof ClientHandshakePacket) {
			final ClientHandshakePacket handshakeResponse = (ClientHandshakePacket) packet;
			encodeHandshakeResponse(handshakeResponse, buf, charset, capabilities);
		} else {
			throw new IllegalStateException("Unknown client packet type: " + packet.getClass());
		}
	}

	private void encodeCommandPacket(ClientCommandPacket packet, ByteBuf buf, Charset charset) {
		buf.writeByte(packet.getCommand().getCommandCode());
		if (packet instanceof ClientQueryPacket) {
			buf.writeCharSequence(((ClientQueryPacket) packet).getQuery(), charset);
		}
	}

	private void encodeHandshakeResponse(ClientHandshakePacket handshakeResponse, ByteBuf buf, Charset charset, Set<CapabilityFlags> capabilities) {
		buf.writeIntLE((int) CodecUtils.toLong(handshakeResponse.getCapabilityFlags()))
				.writeIntLE(handshakeResponse.getMaxPacketSize())
				.writeByte(handshakeResponse.getCharacterSet().getId())
				.writeZero(23);

		CodecUtils.writeNullTerminatedString(buf, handshakeResponse.getUsername(), charset);

		if (capabilities.contains(CapabilityFlags.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA)) {
			CodecUtils.writeLengthEncodedInt(buf, (long) handshakeResponse.getAuthPluginData().writableBytes());
			buf.writeBytes(handshakeResponse.getAuthPluginData());
		} else if (capabilities.contains(CapabilityFlags.CLIENT_SECURE_CONNECTION)) {
			buf.writeByte(handshakeResponse.getAuthPluginData().readableBytes());
			buf.writeBytes(handshakeResponse.getAuthPluginData());
		} else {
			buf.writeBytes(handshakeResponse.getAuthPluginData());
			buf.writeByte(0x00);
		}

		if (capabilities.contains(CapabilityFlags.CLIENT_CONNECT_WITH_DB)) {
			CodecUtils.writeNullTerminatedString(buf, handshakeResponse.getDatabase(), charset);
		}

		if (capabilities.contains(CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
			CodecUtils.writeNullTerminatedString(buf, handshakeResponse.getAuthPluginName(), charset);
		}
		if (capabilities.contains(CapabilityFlags.CLIENT_CONNECT_ATTRS)) {
			CodecUtils.writeLengthEncodedInt(buf, (long) handshakeResponse.getAttributes().size());
			handshakeResponse.getAttributes().forEach((key, value) -> {
				CodecUtils.writeLengthEncodedString(buf, key, charset);
				CodecUtils.writeLengthEncodedString(buf, value, charset);
			});
		}
	}

}
