package com.github.netty.protocol.nrpc;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 *  Data encoder decoder. (Serialization or Deserialization)
 * @author wangzihao
 */
public interface DataCodec {
    Charset CHARSET_UTF8 = StandardCharsets.UTF_8;

    /**
     * Request data - encoding
     * @param data data
     * @param rpcMethod rpcMethod
     * @return ByteBuf
     */
    byte[] encodeRequestData(Object[] data, RpcMethod rpcMethod);

    /**
     * Request data - decoding
     * @param data data
     * @param rpcMethod rpcMethod
     * @return Object[]
     */
    Object[] decodeRequestData(byte[] data, RpcMethod rpcMethod);

    /**
     * Response data - encoding
     * @param data data
     * @return byte[]
     */
    byte[] encodeResponseData(Object data);

    /**
     * Response data - decoding
     * @param data data
     * @return Object
     */
    Object decodeResponseData(byte[] data);


    /**
     * data encode enum  (note: 0=binary, 1=json)
     */
    enum Encode{
        /**
         * binary data encode
         */
        BINARY((byte)0),
        /**
         * json data encode
         */
        JSON((byte) 1);

        private int code;

        Encode(byte code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static Encode indexOf(int code){
            for(Encode encode : values()){
                if(encode.code == code){
                    return encode;
                }
            }
            throw new IllegalArgumentException("value=" + code);
        }
    }
}
