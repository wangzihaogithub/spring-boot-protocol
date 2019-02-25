package com.github.netty.protocol.nrpc;

/**
 *  Data encoder decoder
 * @author wangzihao
 */
public interface DataCodec {

    /**
     * Request data - encoding
     * @param data
     * @param rpcMethod
     * @return
     */
    byte[] encodeRequestData(Object[] data, RpcMethod rpcMethod);

    /**
     * Request data - decoding
     * @param data
     * @param rpcMethod
     * @return
     */
    Object[] decodeRequestData(byte[] data, RpcMethod rpcMethod);

    /**
     * Response data - encoding
     * @param data
     * @return
     */
    byte[] encodeResponseData(Object data);

    /**
     * Response data - decoding
     * @param data
     * @return
     */
    Object decodeResponseData(byte[] data);

}
