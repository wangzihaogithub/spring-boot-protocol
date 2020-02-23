package com.github.netty.protocol.mysql.client;

import com.github.netty.protocol.mysql.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;

import java.util.List;
import java.util.Optional;

/**
 *
 */
public class ClientCommandDecoder extends AbstractPacketDecoder implements ClientDecoder {

	public ClientCommandDecoder() {
		this(Constants.DEFAULT_MAX_PACKET_SIZE);
	}

	public ClientCommandDecoder(int maxPacketSize) {
		super(maxPacketSize);
	}

	@Override
	protected void decodePacket(ChannelHandlerContext ctx, int sequenceId, ByteBuf packet, List<Object> out) {
		final MysqlCharacterSet clientCharset = MysqlCharacterSet.getClientCharsetAttr(ctx.channel());

		final byte commandCode = packet.readByte();
		final Optional<Command> command = Command.findByCommandCode(commandCode);
		if (!command.isPresent()) {
			throw new DecoderException("Unknown command " + commandCode);
		}
		switch (command.get()) {
			case COM_QUERY:
				out.add(new ClientQueryPacket(sequenceId, CodecUtils.readFixedLengthString(packet, packet.readableBytes(), clientCharset.getCharset())));
				break;
			default:
				out.add(new ClientCommandPacket(sequenceId, command.get()));
		}
	}
}
