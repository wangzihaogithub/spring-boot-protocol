package com.github.netty.register.rpc;

/**
 *  数据编码解码器
 * @author 84215
 */
public interface DataCodec {

    /**
     * 请求数据-编码
     * @param data
     * @param rpcMethod
     * @return
     */
    byte[] encodeRequestData(Object[] data, RpcMethod rpcMethod);

    /**
     * 请求数据-解码
     * @param data
     * @param rpcMethod
     * @return
     */
    Object[] decodeRequestData(byte[] data, RpcMethod rpcMethod);

    /**
     * 响应数据-编码
     * @param data
     * @return
     */
    byte[] encodeResponseData(Object data);

    /**
     * 响应数据-解码
     * @param data
     * @return
     */
    Object decodeResponseData(byte[] data);

}
