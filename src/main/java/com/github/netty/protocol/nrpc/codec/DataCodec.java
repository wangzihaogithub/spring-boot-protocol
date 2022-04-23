package com.github.netty.protocol.nrpc.codec;

import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcMethod;
import com.github.netty.protocol.nrpc.RpcServerInstance;

import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Data encoder decoder. (Serialization or Deserialization)
 *
 * @author wangzihao
 */
public interface DataCodec {
    Charset CHARSET_UTF8 = Charset.forName("UTF-8");

    default String buildThrowableRpcMessage(Throwable throwable) {
        return DataCodecUtil.buildThrowableRpcMessage(throwable);
    }

    /**
     * Request data - encoding
     *
     * @param data      data
     * @param rpcMethod rpcMethod
     * @return ByteBuf
     */
    byte[] encodeRequestData(Object[] data, RpcMethod<RpcClient> rpcMethod);

    /**
     * Response last data - decoding
     *
     * @param data      data
     * @param rpcMethod rpcMethod
     * @return Object
     */
    Object decodeResponseData(byte[] data, RpcMethod<RpcClient> rpcMethod);

    /**
     * Response chunk data - decoding
     *
     * @param data      data
     * @param rpcMethod rpcMethod
     * @return Object
     */
    default Object decodeChunkResponseData(byte[] data, RpcMethod<RpcClient> rpcMethod) {
        return decodeChunkResponseData(data, rpcMethod.getChunkGenericReturnType());
    }

    Object decodeChunkResponseData(byte[] data, Type type);

    /**
     * Response chun data - encoding
     *
     * @param data data
     * @return byte[]
     */
    byte[] encodeChunkResponseData(Object data);

    /**
     * Response data - encoding
     *
     * @param data      data
     * @param rpcMethod rpcMethod
     * @return byte[]
     */
    byte[] encodeResponseData(Object data, RpcMethod<RpcServerInstance> rpcMethod);

    /**
     * Request data - decoding
     *
     * @param data      data
     * @param rpcMethod rpcMethod
     * @return Object[]
     */
    Object[] decodeRequestData(byte[] data, RpcMethod<RpcServerInstance> rpcMethod);

    /**
     * The client parses
     *
     * @return EncodeRequestConsumer
     */
    List<Consumer<Map<String, Object>>> getEncodeRequestConsumerList();

    /**
     * The server parses
     *
     * @return DecodeRequestConsumer
     */
    List<Consumer<Map<String, Object>>> getDecodeRequestConsumerList();

    /**
     * data encode enum  (note: 0=binary, 1=json)
     */
    enum Encode {
        /**
         * binary data encode
         */
        BINARY((byte) 0),
        /**
         * app data encode
         */
        APP((byte) 1);

        private int code;

        Encode(byte code) {
            this.code = code;
        }

        public static Encode indexOf(int code) {
            for (Encode encode : values()) {
                if (encode.code == code) {
                    return encode;
                }
            }
            throw new IllegalArgumentException("value=" + code);
        }

        public int getCode() {
            return code;
        }
    }
}
