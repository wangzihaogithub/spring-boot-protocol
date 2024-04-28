package com.github.netty.protocol.dubbo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;

import java.io.*;
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
                ObjectOutput out = serializer.serialize(baos);
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

    public interface ObjectInput extends Closeable {

        Object readObject() throws IOException;

        <T> T readObject(Class<T> cls) throws IOException;

        String readUTF() throws IOException;

        default String readEvent() throws IOException, ClassNotFoundException {
            return readUTF();
        }

        default Object readThrowable() throws IOException, ClassNotFoundException {
            Object obj = readObject();
//            if (!(obj instanceof Throwable)) {
//                throw new IOException("Response data error, expect Throwable, but get " + obj.getClass());
//            }
            return obj;
        }

        default Map<String, Object> readAttachments() throws IOException, ClassNotFoundException {
            return readObject(Map.class);
        }

        void cleanup();

        long skip(long n) throws IOException;

        @Override
        default void close() throws IOException {
            cleanup();
            skip(Integer.MAX_VALUE);
        }
    }

    public interface ObjectOutput {
        void writeObject(Object obj) throws IOException;

        void flushBuffer() throws IOException;

        void cleanup();
    }

}
