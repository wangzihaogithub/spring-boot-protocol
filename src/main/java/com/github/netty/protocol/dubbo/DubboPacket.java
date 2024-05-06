package com.github.netty.protocol.dubbo;

import com.github.netty.protocol.dubbo.packet.BodyFail;
import com.github.netty.protocol.dubbo.packet.BodyHeartBeat;
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

    public ByteBuf getHeaderBytes() {
        return header.bytes();
    }

    public ByteBuf getBodyBytes() {
        return body.bytes();
    }

    public void release() {
        header.release();
        body.release();
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
        } else if (body instanceof BodyHeartBeat) {

        } else if (body instanceof BodyFail) {

        } else {
        }
        return serviceName;
    }

    @Override
    public String toString() {
        return "[" + header.getRequestId() + "]" + (body == null ? "null" : body.getClass().getSimpleName());
    }
}