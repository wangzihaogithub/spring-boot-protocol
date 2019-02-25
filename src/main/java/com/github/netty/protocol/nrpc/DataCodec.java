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

}
