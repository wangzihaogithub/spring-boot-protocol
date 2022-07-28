package com.github.netty.protocol.nrpc;

import com.github.netty.protocol.nrpc.exception.RpcResponseException;

/**
 * rpc done callback
 *
 * @author wangzihao
 */
public interface RpcDone {
    /**
     * on chunk callback
     *
     * @param rpcResponse rpcResponse
     * @param ack         ack
     */
    void chunk(RpcPacket.ResponseChunkPacket rpcResponse, ChunkAck ack);

    /**
     * on done callback
     *
     * @param rpcResponse rpcResponse
     */
    void done(RpcPacket.ResponseLastPacket rpcResponse);

    /**
     * on timeout
     *
     * @param requestId       requestId
     * @param createTimestamp createTimestamp
     * @param expiryTimestamp expiryTimestamp
     */
    void doneTimeout(int requestId, long createTimestamp, long expiryTimestamp);

    /**
     * If an exception state is returned, an exception is thrown
     * All response states above 400 are in error
     *
     * @param response response
     * @throws RpcResponseException RpcResponseException
     */
    default void handlerResponseIfNeedThrow(RpcPacket.ResponsePacket response) throws RpcResponseException {
        if (response == null) {
            return;
        }

        Integer status = response.getStatus();
        if (status == null || status >= RpcPacket.ResponsePacket.NO_SUCH_METHOD) {
            throw new RpcResponseException(status, "Failure rpc response. status=" + status + ",message=" + response.getMessage() + ",response=" + response, true);
        }
    }

    @FunctionalInterface
    interface ChunkListener<CHUNK> {
        void onChunk(CHUNK chunk, int chunkId, ChunkAck ack);
    }

}
