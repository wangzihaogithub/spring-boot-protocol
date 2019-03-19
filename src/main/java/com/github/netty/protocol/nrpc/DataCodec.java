package com.github.netty.protocol.nrpc;

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
        BINARY(0),
        /**
         * json data encode
         */
        JSON(1);

        private int index;

        Encode(int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }

        public static Encode indexOf(int index){
            for(Encode encode : values()){
                if(encode.index == index){
                    return encode;
                }
            }
            return null;
        }

    }
}
