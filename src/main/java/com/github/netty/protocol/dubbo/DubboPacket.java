package com.github.netty.protocol.dubbo;

import com.github.netty.protocol.dubbo.packet.BodyEvent;
import com.github.netty.protocol.dubbo.packet.BodyFail;
import com.github.netty.protocol.dubbo.packet.BodyRequest;
import com.github.netty.protocol.dubbo.packet.BodyResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.Map;
import java.util.Objects;

public class DubboPacket {
    final Header header;
    Body body;

    public DubboPacket(Header header) {
        this.header = header;
    }

    public Header getHeader() {
        return header;
    }

    public Body getBody() {
        return body;
    }

    public boolean isHeartBeat() {
        return header.isEvent()
                && body instanceof BodyEvent
                && Objects.equals(Constant.HEARTBEAT_EVENT, ((BodyEvent) body).getEvent());
    }

    public void release() {
        header.release();
        if (body != null) {
            body.release();
        }
    }

    public ByteBuf buildErrorPacket(ByteBufAllocator allocator, byte errorStatus, String errorMessage) {
        byte serializationProtoId = header.getSerializationProtoId();
        byte[] errorBytes = Serialization.getStringBytesOf(serializationProtoId, errorMessage);
        int maxCapacity = Constant.HEADER_LENGTH + errorBytes.length;

        ByteBuf empty = allocator.ioBuffer(maxCapacity, maxCapacity);
        empty.writeByte(Constant.MAGIC_0);
        empty.writeByte(Constant.MAGIC_1);
        empty.writeByte(serializationProtoId);
        empty.writeByte(errorStatus);
        empty.writeLong(header.getRequestId());
        empty.writeInt(errorBytes.length);
        empty.writeBytes(errorBytes);
        return empty;
    }

    public static ByteBuf buildHeartbeatPacket(ByteBufAllocator allocator,
                                               byte serializationProtoId,
                                               long requestId,
                                               byte status,
                                               boolean request,
                                               boolean twoway) {
        byte[] nullBytes = Serialization.getNullBytesOf(serializationProtoId);
        int maxCapacity = Constant.HEADER_LENGTH + nullBytes.length;

        int flag = serializationProtoId | Constant.FLAG_EVENT;
        if (request) {
            flag |= Constant.FLAG_REQUEST;
        }
        if (twoway) {
            flag |= Constant.FLAG_TWOWAY;
        }
        ByteBuf empty = allocator.ioBuffer(maxCapacity, maxCapacity);
        empty.writeByte(Constant.MAGIC_0);
        empty.writeByte(Constant.MAGIC_1);
        empty.writeByte(flag);
        empty.writeByte(status);
        empty.writeLong(requestId);
        empty.writeInt(nullBytes.length);
        empty.writeBytes(nullBytes);
        return empty;
    }

    public String getRequestPath() {
        if (body instanceof BodyRequest) {
            return ((BodyRequest) body).getPath();
        } else {
            return null;
        }
    }

    public String getRequestMethodName() {
        if (body instanceof BodyRequest) {
            return ((BodyRequest) body).getMethodName();
        } else {
            return null;
        }
    }

    public String getAttachmentValue(String attachmentName) {
        if (attachmentName == null || attachmentName.isEmpty()) {
            return null;
        }
        String serviceName = null;
        if (body instanceof BodyRequest) {
            Map<String, Object> attachments = ((BodyRequest) body).getAttachments();
            if (attachments != null) {
                serviceName = Objects.toString(attachments.get(attachmentName), null);
            }
        } else if (body instanceof BodyResponse) {
            Map<String, Object> attachments = ((BodyResponse) body).getAttachments();
            if (attachments != null) {
                serviceName = Objects.toString(attachments.get(attachmentName), null);
            }
        } else if (body instanceof BodyEvent) {

        } else if (body instanceof BodyFail) {

        } else {
        }
        return serviceName;
    }

    @Override
    public String toString() {
        return "DubboPacket{" +
                "\n" + header +
                ",\n" + body +
                '}';
    }
}