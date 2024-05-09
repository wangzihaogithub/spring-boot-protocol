package com.github.netty.protocol.dubbo;

import com.github.netty.protocol.dubbo.packet.BodyFail;
import com.github.netty.protocol.dubbo.packet.BodyHeartBeat;
import com.github.netty.protocol.dubbo.packet.BodyRequest;
import com.github.netty.protocol.dubbo.packet.BodyResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.github.netty.protocol.dubbo.Constant.*;

public class DubboDecoder extends ByteToMessageDecoder {
    private State state = State.READ_HEADER;
    private DubboPacket packet;

    public static boolean isDubboProtocol(ByteBuf buffer) {
        return buffer.readableBytes() >= 2
                && buffer.getByte(0) == MAGIC_0
                && buffer.getByte(1) == MAGIC_1;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        boolean hasNext;
        do {
            switch (this.state) {
                case READ_HEADER: {
                    if (buffer.readableBytes() >= HEADER_LENGTH) {
                        try {
                            this.packet = new DubboPacket(readHeader(buffer));
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
                            this.packet.body = readBody(body);
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

    protected Header readHeader(ByteBuf buffer) {
        // request and serialization flag.
        byte flag = buffer.getByte(2);
        byte status = buffer.getByte(3);
        long requestId = buffer.getLong(4);
        // 8 - 1-request/0-response
        byte type = buffer.getByte(8);
        int bodyLength = buffer.getInt(12);

        ByteBuf headerBytes = buffer.readRetainedSlice(HEADER_LENGTH);
        return new Header(headerBytes, flag, status, requestId, type, bodyLength);
    }

    protected Body readBody(ByteBuf buffer) throws IOException, ClassNotFoundException {
        // request and serialization flag.
        byte flag = packet.header.flag;
        byte status = packet.header.status;
        int bodyLength = packet.header.bodyLength;
        boolean flagResponse = (flag & FLAG_REQUEST) == 0;
        byte serializationProtoId = (byte) (flag & SERIALIZATION_MASK);
        if (flagResponse) {
            // decode response.
            if (status == OK) {
                if ((flag & FLAG_EVENT) != 0) {
                    return readHeartBeat(buffer, bodyLength, serializationProtoId);
                } else {
                    try (Serialization.ObjectInput in = Serialization.codeOfDeserialize(serializationProtoId, buffer, bodyLength)) {
                        byte responseWith = buffer.readByte();
                        BodyResponse packetResponse;
                        switch (responseWith) {
                            case RESPONSE_NULL_VALUE:
                                packetResponse = new BodyResponse(null, null, null);
                                break;
                            case RESPONSE_VALUE:
                                packetResponse = new BodyResponse(in.readObject(), null, null);
                                break;
                            case RESPONSE_WITH_EXCEPTION:
                                packetResponse = new BodyResponse(null, in.readThrowable(), null);
                                break;
                            case RESPONSE_NULL_VALUE_WITH_ATTACHMENTS:
                                packetResponse = new BodyResponse(null, null, in.readAttachments());
                                break;
                            case RESPONSE_VALUE_WITH_ATTACHMENTS:
                                packetResponse = new BodyResponse(in.readObject(), null, in.readAttachments());
                                break;
                            case RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS:
                                packetResponse = new BodyResponse(null, in.readThrowable(), in.readAttachments());
                                break;
                            default:
                                throw new IOException("Unknown result flag, expect '0' '1' '2' '3' '4' '5', but received: " + responseWith);
                        }
                        return packetResponse;
                    }
                }
            } else {
                try (Serialization.ObjectInput in = Serialization.codeOfDeserialize(serializationProtoId, buffer, bodyLength)) {
                    return new BodyFail(in.readUTF());
                }
            }
        } else {
            // decode request.
            if ((flag & FLAG_EVENT) != 0) {
                return readHeartBeat(buffer, bodyLength, serializationProtoId);
            } else {
                try (Serialization.ObjectInput in = Serialization.codeOfDeserialize(serializationProtoId, buffer, bodyLength)) {
                    String dubboVersion = in.readUTF();
                    String path = in.readUTF();
                    String version = in.readUTF();
                    String methodName = in.readUTF();
                    String parameterTypesDesc = in.readUTF();
                    int countArgs = countArgs(parameterTypesDesc);
                    Object[] args = new Object[countArgs];
                    for (int i = 0; i < countArgs; i++) {
                        args[i] = in.readArg();
                    }
                    Map<String, Object> attachments = in.readAttachments();
                    return new BodyRequest(dubboVersion, path, version, methodName, parameterTypesDesc, attachments, args);
                }
            }
        }
    }

    protected BodyHeartBeat readHeartBeat(ByteBuf buffer, int bodyLength, byte serializationProtoId) throws IOException, ClassNotFoundException {
        Object data;
        byte[] payload = Serialization.getPayload(buffer, bodyLength);
        if (Serialization.isHeartBeat(payload, serializationProtoId)) {
            data = null;
        } else {
            try (Serialization.ObjectInput input = Serialization.codeOfDeserialize(serializationProtoId, new ByteArrayInputStream(payload))) {
                data = input.readEvent();
            }
        }
        return new BodyHeartBeat(data);
    }

    protected <E extends Exception> void exception(ChannelHandlerContext ctx, ByteBuf buffer, E cause) throws Exception {
        buffer.release();
        ctx.close();
    }

    protected enum State {
        READ_HEADER, READ_BODY
    }
}