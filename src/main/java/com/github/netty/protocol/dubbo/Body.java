package com.github.netty.protocol.dubbo;

import com.github.netty.protocol.dubbo.packet.BodyEvent;
import com.github.netty.protocol.dubbo.packet.BodyFail;
import com.github.netty.protocol.dubbo.packet.BodyRequest;
import com.github.netty.protocol.dubbo.packet.BodyResponse;
import io.netty.buffer.ByteBuf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static com.github.netty.protocol.dubbo.Constant.*;

public abstract class Body {
    ByteBuf bodyBytes;
    int markReaderIndex;

    public ByteBuf encode() {
        if (bodyBytes != null) {
            bodyBytes.readerIndex(markReaderIndex);
        }
        return bodyBytes;
    }

    public boolean release() {
        if (bodyBytes != null && bodyBytes.refCnt() > 0) {
            return bodyBytes.release();
        } else {
            return false;
        }
    }

    public static Body readBody(ByteBuf buffer, Header header) throws IOException, ClassNotFoundException {
        // request and serialization flag.
        byte flag = header.flag;
        byte status = header.status;
        int bodyLength = header.bodyLength;
        boolean flagResponse = (flag & FLAG_REQUEST) == 0;
        byte serializationProtoId = (byte) (flag & SERIALIZATION_MASK);
        if (flagResponse) {
            // decode response.
            if (status == OK) {
                if ((flag & FLAG_EVENT) != 0) {
                    return readEvent(buffer, bodyLength, serializationProtoId);
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
                return readEvent(buffer, bodyLength, serializationProtoId);
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

    public static BodyEvent readEvent(ByteBuf buffer, int bodyLength, byte serializationProtoId) throws IOException, ClassNotFoundException {
        Object data;
        byte[] payload = Serialization.getPayload(buffer, bodyLength);
        if (Serialization.isHeartBeat(payload, serializationProtoId)) {
            data = null;
        } else {
            try (Serialization.ObjectInput input = Serialization.codeOfDeserialize(serializationProtoId, new ByteArrayInputStream(payload))) {
                data = input.readEvent();
            }
        }
        return new BodyEvent(data);
    }

}
