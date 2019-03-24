package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.RecyclableUtil;
import io.netty.buffer.ByteBuf;

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
    ByteBuf encodeRequestData(Object[] data, RpcMethod rpcMethod);

    /**
     * Request data - decoding
     * @param data data
     * @param rpcMethod rpcMethod
     * @return Object[]
     */
    Object[] decodeRequestData(ByteBuf data, RpcMethod rpcMethod);

    /**
     * Response data - encoding
     * @param data data
     * @return byte[]
     */
    ByteBuf encodeResponseData(Object data);

    /**
     * Response data - decoding
     * @param data data
     * @return Object
     */
    Object decodeResponseData(ByteBuf data);


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
        private byte[] codeBytes;
        private ByteBuf codeByteBuf;

        Encode(byte code) {
            this.code = code;
            this.codeBytes = new byte[]{code};
            this.codeByteBuf = RecyclableUtil.newReadOnlyBuffer(codeBytes);
        }

        public int getCode() {
            return code;
        }

        public ByteBuf getByteBuf() {
            return RecyclableUtil.newReadOnlyBuffer(codeBytes);
        }

        public static Encode indexOf(ByteBuf codeByteBuf){
            for(Encode encode : values()){
                if(encode.codeByteBuf.equals(codeByteBuf)){
                    return encode;
                }
            }
            throw new IllegalArgumentException("value=" + codeByteBuf.toString(CHARSET_UTF8));
        }
    }
}
