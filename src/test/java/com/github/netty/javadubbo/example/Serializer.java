package com.github.netty.javadubbo.example;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface Serializer {
    Map<Byte, Serializer> INSTANCES = new ConcurrentHashMap<>();
    // Cache null object serialize results, for heartbeat request/response serialize use.
    Map<Byte, byte[]> ID_NULLBYTES_MAP = new ConcurrentHashMap<>();

    static ObjectOutput codeOfSerialize(byte serializationProtoId, OutputStream buffer) throws IOException {
        Serializer serializer = Serializer.codeOf(serializationProtoId);
        return serializer.serialize(buffer);
    }

    static ObjectInput codeOfDeserialize(byte serializationProtoId, InputStream inputStream) throws IOException {
        Serializer serializer = Serializer.codeOf(serializationProtoId);
        return serializer.deserialize(inputStream);
    }

    static ObjectInput codeOfDeserialize(byte serializationProtoId, ByteBuf buffer, int bodyLength) throws IOException {
        Serializer serializer = Serializer.codeOf(serializationProtoId);
        return serializer.deserialize(new ByteBufInputStream(buffer, bodyLength, false));
    }

    static Serializer codeOf(byte serializationProtoId) {
        return INSTANCES.computeIfAbsent(serializationProtoId, k -> {
            switch (serializationProtoId) {
                case 2: {
                    return new Hessian2Serializer(serializationProtoId);
                }
                default: {
                    return null;
                }
            }
        });
    }

    static boolean isHeartBeat(byte[] payload, byte proto) {
        return Arrays.equals(payload, getNullBytesOf(proto));
    }

    static byte[] getPayload(ByteBuf buffer, int length) {
        return ByteBufUtil.getBytes(buffer, buffer.readerIndex(), length);
    }

    static byte[] getNullBytesOf(byte protserializationProtoId) {
        return ID_NULLBYTES_MAP.computeIfAbsent(protserializationProtoId, key -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Serializer serializer = Serializer.codeOf(key);
                Serializer.ObjectOutput out = serializer.serialize(baos);
                out.writeObject(null);
                out.flushBuffer();
                return baos.toByteArray();
            } catch (Exception e) {
//                        logger.warn(
//                                TRANSPORT_FAILED_SERIALIZATION,
//                                "",
//                                "",
//                                "Serialization extension " + s.getClass().getName()
//                                        + " not support serializing null object, return an empty bytes instead.");
                return new byte[0];
            }
        });
    }

    byte getContentTypeId();

    ObjectOutput serialize(OutputStream output) throws IOException;

    ObjectInput deserialize(InputStream input) throws IOException;

    public interface ObjectInput {

        Object readObject() throws IOException;

        <T> T readObject(Class<T> cls) throws IOException;

        String readUTF() throws IOException;

        default String readEvent() throws IOException, ClassNotFoundException {
            return readUTF();
        }

        default Map<String, Object> readAttachments() throws IOException, ClassNotFoundException {
            return readObject(Map.class);
        }
    }

    public interface ObjectOutput {
        void writeObject(Object obj) throws IOException;

        void flushBuffer() throws IOException;
    }

}
