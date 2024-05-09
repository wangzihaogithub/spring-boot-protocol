package com.github.netty.protocol.dubbo;

import com.github.netty.protocol.dubbo.serialization.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface Serialization {
    Map<Byte, Serialization> INSTANCES = new ConcurrentHashMap<>();
    // Cache null object serialize results, for heartbeat request/response serialize use.
    Map<Byte, byte[]> ID_NULLBYTES_MAP = new ConcurrentHashMap<>();
    Map<Byte, Map<String, byte[]>> ID_STRING_BYTES_MAP = new ConcurrentHashMap<>();

    static ObjectInput codeOfDeserialize(byte serializationProtoId, InputStream inputStream) throws IOException {
        Serialization serializer = Serialization.codeOf(serializationProtoId);
        return serializer.deserialize(inputStream);
    }

    static ObjectInput codeOfDeserialize(byte serializationProtoId, ByteBuf buffer, int bodyLength) throws IOException {
        Serialization serializer = Serialization.codeOf(serializationProtoId);
        return serializer.deserialize(new ByteBufInputStream(buffer, bodyLength, false));
    }

    static Serialization codeOf(byte serializationProtoId) {
        return INSTANCES.computeIfAbsent(serializationProtoId, key -> {
            switch (key) {
                case 2: {
                    return new Hessian2Serialization(key);
                }
                case 3: {
                    return new JavaSerialization(key);
                }
                case 4: {
                    return new CompactedJavaSerialization(key);
                }
                case 7: {
                    return new NativeJavaSerialization(key);
                }
                case 23: {
                    return new FastJson2Serialization(key);
                }
                default: {
                    throw new UnsupportedOperationException("unsupported serialization proto id: " + key);
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

    static byte[] getNullBytesOf(byte serializationProtoId) {
        return ID_NULLBYTES_MAP.computeIfAbsent(serializationProtoId, key -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Serialization serializer = Serialization.codeOf(key);
                ObjectOutput out = serializer.serialize(baos);
                out.writeObject(null);
                out.flushBuffer();
                byte[] byteArray = baos.toByteArray();
                out.cleanup();
                return byteArray;
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

    static byte[] getStringBytesOf(byte serializationProtoId, String cacheKey) {
        Map<String, byte[]> stringMap = ID_STRING_BYTES_MAP.computeIfAbsent(serializationProtoId,
                key -> Collections.synchronizedMap(new LinkedHashMap(6, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry eldest) {
                        return size() > 30;
                    }
                }));
        return stringMap.computeIfAbsent(cacheKey, key -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Serialization serializer = Serialization.codeOf(serializationProtoId);
                ObjectOutput out = serializer.serialize(baos);
                out.writeUTF(key);
                out.flushBuffer();
                byte[] byteArray = baos.toByteArray();
                out.cleanup();
                return byteArray;
            } catch (Exception e) {
                return new byte[0];
            }
        });
    }

    byte getContentTypeId();

    ObjectOutput serialize(OutputStream output) throws IOException;

    ObjectInput deserialize(InputStream input) throws IOException;

    public interface ObjectInput extends Closeable {

        default Object readArg() throws IOException, ClassNotFoundException {
            return readObject();
        }

        Object readObject() throws IOException, ClassNotFoundException;

        <T> T readObject(Class<T> cls) throws IOException, ClassNotFoundException;

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

        void writeUTF(String obj) throws IOException;

        void flushBuffer() throws IOException;

        void cleanup();
    }

}
