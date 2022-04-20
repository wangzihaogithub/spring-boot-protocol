package com.github.netty.protocol.nrpc;

import com.github.netty.protocol.nrpc.exception.RpcResponseException;
import io.netty.channel.ChannelHandlerContext;

/**
 * rpc done callback
 * @author wangzihao
 */
public interface RpcDone {
    @FunctionalInterface
    interface ChunkListener<CHUNK> {
        void onChunk(CHUNK chunk, RpcPacket.ResponseChunkPacket rpcResponse,ChannelHandlerContext ctx);
    }

    /**
     * on chunk callback
     * @param rpcResponse rpcResponse
     * @param ctx ack
     */
    void chunk(RpcPacket.ResponseChunkPacket rpcResponse, ChannelHandlerContext ctx);

    /**
     * on done callback
     * @param rpcResponse rpcResponse
     */
    void done(RpcPacket.ResponseLastPacket rpcResponse);

    /**
     * on timeout
     * @param requestId requestId
     * @param createTimestamp createTimestamp
     * @param expiryTimestamp expiryTimestamp
     */
    void doneTimeout(int requestId,long createTimestamp,long expiryTimestamp);

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
