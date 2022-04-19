package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.protocol.nrpc.exception.RpcException;
import com.github.netty.protocol.nrpc.exception.RpcTimeoutException;
import com.github.netty.protocol.nrpc.exception.RpcWriteException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.socket.SocketChannel;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.RpcClientAop.CONTEXT_LOCAL;
import static com.github.netty.protocol.nrpc.RpcContext.RpcState.*;
import static com.github.netty.protocol.nrpc.RpcPacket.ACK_YES;

/**
 * async response.
 *
 * @author wangzihao
 * 2019/11/3/019
 */
public class RpcClientReactivePublisher implements Publisher<Object>, Subscription, RpcDone {
    private long currentRequestCount;
    private volatile boolean cancelFlag = false;
    private volatile Subscriber<? super Object> subscriber;
    private final RpcContext<RpcClient> rpcContext;
    private final RpcClient rpcClient;
    private final DataCodec dataCodec;
    private final String requestMappingName;
    private final String version;
    private int timeout;
    private ChunkListener chunkListener;

    RpcClientReactivePublisher(RpcContext<RpcClient> rpcContext, String requestMappingName, String version, int timeout) {
        this.rpcContext = rpcContext;
        this.rpcClient = rpcContext.getRpcMethod().getInstance();
        this.dataCodec = rpcClient.getDataCodec();
        this.requestMappingName = requestMappingName;
        this.version = version;
        this.timeout = timeout;
    }

    @Override
    public void chunk(RpcPacket.ResponseChunkPacket rpcResponse) {
        if (cancelFlag || !rpcContext.getRpcMethod().isReturnChunkCompletionStageFlag()) {
            RecyclableUtil.release(rpcResponse);
            return;
        }
        ChunkListener chunkListener = this.chunkListener;
        if (chunkListener != null) {
            CONTEXT_LOCAL.set(rpcContext);
            try {
                Object result;
                if (rpcResponse.getEncode() == BINARY) {
                    result = rpcResponse.getData();
                } else {
                    result = dataCodec.decodeChunkResponseData(rpcResponse.getData(), rpcContext.getRpcMethod());
                }
                chunkListener.onChunk(result, rpcResponse);
                rpcClient.onStateUpdate(rpcContext, READ_CHUNK);
            } finally {
                RecyclableUtil.release(rpcResponse);
                CONTEXT_LOCAL.set(null);
            }
        }
    }

    @Override
    public void done(RpcPacket.ResponseLastPacket rpcResponse) {
        if (cancelFlag) {
            RecyclableUtil.release(rpcResponse);
            return;
        }
        rpcContext.setRpcEndTimestamp(System.currentTimeMillis());
        CONTEXT_LOCAL.set(rpcContext);
        try {
            rpcContext.setResponse(rpcResponse);
            rpcClient.onStateUpdate(rpcContext, READ_ING);

            handlerResponseIfNeedThrow(rpcResponse);

            //If the server is not encoded, return directly
            Object result;
            if (rpcResponse.getEncode() == BINARY) {
                result = rpcResponse.getData();
            } else {
                result = dataCodec.decodeResponseData(rpcResponse.getData(), rpcContext.getRpcMethod());
            }

            rpcContext.setResult(result);
            rpcClient.onStateUpdate(rpcContext, READ_FINISH);
            subscriber.onNext(result);
        } catch (Throwable t) {
            rpcContext.setThrowable(t);
            subscriber.onError(t);
        } finally {
            subscriber.onComplete();
            rpcClient.onStateUpdate(rpcContext, END);
            try {
                for (RpcClientAop aop : rpcClient.getAopList()) {
                    aop.onResponseAfter(rpcContext);
                }
            } finally {
                RecyclableUtil.release(rpcResponse);
                CONTEXT_LOCAL.set(null);
            }
        }
    }

    @Override
    public void doneTimeout(int requestId, long createTimestamp, long expiryTimestamp) {
        rpcContext.setRpcEndTimestamp(expiryTimestamp);
        RpcTimeoutException timeoutException = new RpcTimeoutException("RpcRequestTimeout : maxTimeout = [" + (expiryTimestamp - createTimestamp) +
                "], timeout = [" + (System.currentTimeMillis() - createTimestamp) + "], [" + toString() + "]", true,
                createTimestamp, expiryTimestamp);
        rpcContext.getRpcMethod().getInstance().getWorker().execute(
                () -> {
                    try {
                        CONTEXT_LOCAL.set(rpcContext);
                        rpcClient.onStateUpdate(rpcContext, TIMEOUT);
                        rpcContext.setThrowable(timeoutException);
                        subscriber.onError(timeoutException);
                    } finally {
                        subscriber.onComplete();
                        try {
                            for (RpcClientAop aop : rpcClient.getAopList()) {
                                aop.onTimeout(rpcContext);
                            }
                        } finally {
                            CONTEXT_LOCAL.set(null);
                        }
                    }
                }
        );
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("non-positive request");
        }
        if (cancelFlag) {
            return;
        }
        rpcContext.setRpcBeginTimestamp(System.currentTimeMillis());
        currentRequestCount += n;

        CONTEXT_LOCAL.set(rpcContext);
        int requestId = rpcClient.newRequestId();
        try {
            rpcContext.setRemoteAddress(rpcClient.getRemoteAddress());
            SocketChannel channel = rpcClient.getChannel();
            rpcContext.setRemoteAddress(channel.remoteAddress());
            rpcContext.setLocalAddress(channel.localAddress());

            RpcPacket.RequestPacket rpcRequest = RpcPacket.RequestPacket.newInstance();
            rpcRequest.setRequestId(requestId);
            rpcRequest.setRequestMappingName(requestMappingName);
            rpcRequest.setVersion(version);
            rpcRequest.setMethodName(rpcContext.getRpcMethod().getMethodName());
            rpcRequest.setAck(ACK_YES);
            rpcRequest.setTimeout(timeout);
            rpcContext.setRequest(rpcRequest);
            rpcContext.setTimeout(timeout);
            rpcClient.onStateUpdate(rpcContext, INIT);

            rpcRequest.setData(dataCodec.encodeRequestData(rpcContext.getArgs(), rpcContext.getRpcMethod()));
            rpcClient.onStateUpdate(rpcContext, WRITE_ING);

            rpcRequest.setTimeout(timeout);
            ChannelFuture writeAndFlushFuture = channel.writeAndFlush(rpcRequest);
            rpcClient.rpcDoneMap.put(requestId, this, timeout);
            writeAndFlushFuture.addListener((ChannelFutureListener) future -> {
                CONTEXT_LOCAL.set(rpcContext);
                try {
                    if (future.isSuccess()) {
                        rpcClient.onStateUpdate(rpcContext, WRITE_FINISH);
                    } else {
                        Throwable throwable = future.cause();
                        future.channel().close().addListener(f -> rpcClient.connect());
                        handlerRpcWriterException(new RpcWriteException("rpc write exception. " + throwable, throwable), requestId);
                    }
                } finally {
                    CONTEXT_LOCAL.set(null);
                }
            });
        } catch (RpcException rpcException) {
            handlerRpcWriterException(rpcException, requestId);
        } finally {
            CONTEXT_LOCAL.set(null);
        }
    }

    private void handlerRpcWriterException(RpcException rpcException, int requestId) {
        rpcContext.setRpcEndTimestamp(System.currentTimeMillis());
        rpcClient.rpcDoneMap.remove(requestId);
        rpcContext.setThrowable(rpcException);
        subscriber.onError(rpcException);
    }

    @Override
    public void cancel() {
        this.cancelFlag = true;
    }

    @Override
    public void subscribe(Subscriber<? super Object> subscriber) {
        this.subscriber = subscriber;
        if (subscriber instanceof ChunkListener) {
            this.chunkListener = (ChunkListener) subscriber;
        }
        CONTEXT_LOCAL.set(rpcContext);
        try {
            subscriber.onSubscribe(this);
        } finally {
            CONTEXT_LOCAL.set(null);
        }
    }

    public long getCurrentRequestCount() {
        return currentRequestCount;
    }

    @Override
    public String toString() {
        RpcPacket.RequestPacket request = rpcContext.getRequest();
        return "RpcClientReactivePublisher@" + super.hashCode() + "{state=" + rpcContext.getState() + ","
                + requestMappingName + ":" + version + '/' + (request == null ? "" : request.getMethodName()) + "}";
    }

    public boolean isCancel() {
        return cancelFlag;
    }

    public RpcContext<RpcClient> getRpcContext() {
        return rpcContext;
    }
}
