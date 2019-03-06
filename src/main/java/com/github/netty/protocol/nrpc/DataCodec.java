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
     * data encode enum
     */
    enum Encode{
        /**
         * json data encode
         */
        JSON("json",(byte)1),
        /**
         * binary data encode
         */
        BINARY("binary",(byte) 0);

        private String name;
        private int id;

        Encode(String name,int id) {
            this.name = name;
            this.id = id;
        }

        public static Encode indexOf(int id){
            for(Encode encode : values()){
                if(encode.id == id){
                    return encode;
                }
            }
            return null;
        }

        public String getName() {
            return name;
        }

        public int getId() {
            return id;
        }
    }
}
