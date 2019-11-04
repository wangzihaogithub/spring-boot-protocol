package com.github.netty.protocol.nrpc;

import com.github.netty.protocol.nrpc.exception.RpcResponseException;

/**
 * rpc done callback
 * @author wangzihao
 */
public interface RpcDone {
    /**
     * on done callback
     * @param rpcResponse rpcResponse
     */
    void done(RpcPacket.ResponsePacket rpcResponse);

    /**
     * If an exception state is returned, an exception is thrown
     * All response states above 400 are in error
     * @param response response
     * @throws RpcResponseException RpcResponseException
     */
    default void handlerResponseIfNeedThrow(RpcPacket.ResponsePacket response) throws RpcResponseException {
        if(response == null) {
            return;
        }

        Integer status = response.getStatus();
        if(status == null || status >= RpcPacket.ResponsePacket.NO_SUCH_METHOD){
            throw new RpcResponseException(status,"Failure rpc response. status="+status+",message="+response.getMessage()+",response="+response,true);
        }
    }

}
