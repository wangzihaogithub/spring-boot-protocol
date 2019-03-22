package com.github.netty.protocol.nrpc;

import io.netty.util.AsciiString;

/**
 *  Data encoder decoder
 * @author wangzihao
 */
public interface DataCodec {

    /**
     * Request data - encoding
     * @param data data
     * @param rpcMethod rpcMethod
     * @return byte[]
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
        private AsciiString ascii;

        Encode(byte code) {
            this.code = code;
            this.ascii = new AsciiString(new byte[]{code},false);
        }

        public int getCode() {
            return code;
        }

        public AsciiString getAscii() {
            return ascii;
        }

        public static Encode indexOf(AsciiString ascii){
            for(Encode encode : values()){
                if(encode.ascii.equals(ascii)){
                    return encode;
                }
            }
            return null;
        }
    }
}
