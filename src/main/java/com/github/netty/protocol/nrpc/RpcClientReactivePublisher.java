package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.protocol.nrpc.exception.RpcException;
import com.github.netty.protocol.nrpc.exception.RpcWriteException;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.socket.SocketChannel;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static com.github.netty.protocol.nrpc.DataCodec.Encode.BINARY;
import static com.github.netty.protocol.nrpc.RpcClientAop.CONTEXT_LOCAL;
import static com.github.netty.protocol.nrpc.RpcContext.State.*;
import static com.github.netty.protocol.nrpc.RpcPacket.ACK_YES;

/**
 * async response.
 * @author wangzihao
 *  2019/11/3/019
 */
public class RpcClientReactivePublisher implements Publisher<Object>,Subscription,RpcDone {
    private static final long MAX_REQUEST_COUNT = Long.MAX_VALUE;
    private long currentRequestCount;
    private volatile boolean cancelFlag = false;
    private volatile Subscriber<? super Object> subscriber;
    private final RpcContext<RpcClient> rpcContext;
    private final RpcClient rpcClient;
    private final DataCodec dataCodec;
    private final String requestMappingName;

    RpcClientReactivePublisher(RpcContext<RpcClient> rpcContext, String requestMappingName) {
        this.rpcContext = rpcContext;
        this.rpcClient = rpcContext.getRpcMethod().getInstance();
        this.dataCodec = rpcClient.getDataCodec();
        this.requestMappingName = requestMappingName;
    }

    @Override
    public void done(RpcPacket.ResponsePacket rpcResponse) {
        if(cancelFlag){
            return;
        }
        int requestId = rpcResponse.getRequestId();
        CONTEXT_LOCAL.set(rpcContext);
        try {
            rpcContext.setResponse(rpcResponse);
            rpcContext.setState(READ_ING);
            rpcClient.onStateUpdate(rpcContext);

            handlerResponseIfNeedThrow(rpcResponse);

            //If the server is not encoded, return directly
            Object result;
            if (rpcResponse.getEncode() == BINARY) {
                result = rpcResponse.getData();
            } else {
                result = dataCodec.decodeResponseData(rpcResponse.getData(), rpcContext.getRpcMethod());
            }

            rpcContext.setResult(result);
            rpcContext.setState(READ_FINISH);
            rpcClient.onStateUpdate(rpcContext);

            subscriber.onNext(result);
            subscriber.onComplete();
        }catch (Throwable t){
            rpcContext.setThrowable(t);
            subscriber.onError(t);
        }finally {
            try {
                for (RpcClientAop aop : rpcClient.getAopList()) {
                    aop.onResponseAfter(rpcContext);
                }
            }finally {
                rpcClient.rpcDoneMap.remove(requestId);
                RecyclableUtil.release(rpcResponse);
                rpcContext.recycle();
                CONTEXT_LOCAL.set(null);
            }
        }
    }

    @Override
    public void request(long n) {
        if(n <= 0){
            throw new IllegalArgumentException("non-positive request");
        }
        if(cancelFlag){
            return;
        }
        currentRequestCount += n;

        CONTEXT_LOCAL.set(rpcContext);
        try {
            int requestId = rpcClient.newRequestId();
            RpcPacket.RequestPacket rpcRequest = RpcPacket.RequestPacket.newInstance();
            rpcRequest.setRequestId(requestId);
            rpcRequest.setRequestMappingName(requestMappingName);
            rpcRequest.setMethodName(rpcContext.getRpcMethod().getMethod().getName());
            rpcRequest.setAck(ACK_YES);

            rpcContext.setRequest(rpcRequest);
            rpcContext.setState(INIT);
            rpcClient.onStateUpdate(rpcContext);

            rpcRequest.setData(dataCodec.encodeRequestData(rpcContext.getArgs(), rpcContext.getRpcMethod()));
            rpcContext.setState(WRITE_ING);
            rpcClient.onStateUpdate(rpcContext);

            rpcClient.rpcDoneMap.put(requestId, this);
            try {
                SocketChannel channel = rpcClient.getChannel();
                rpcContext.setRemoteAddress(channel.remoteAddress());
                rpcContext.setLocalAddress(channel.localAddress());
                channel.writeAndFlush(rpcRequest).addListener((ChannelFutureListener) future -> {
                    CONTEXT_LOCAL.set(rpcContext);
                    try {
                        if (future.isSuccess()) {
                            rpcContext.setState(WRITE_FINISH);
                            rpcClient.onStateUpdate(rpcContext);
                        }else {
                            Throwable throwable = future.cause();
                            future.channel().close().addListener(f -> rpcClient.connect());
                            handlerRpcWriterException(new RpcWriteException("rpc write exception. "+throwable,throwable),requestId);
                        }
                    } finally {
                        CONTEXT_LOCAL.set(null);
                    }
                });
            }catch (RpcException rpcException){
                handlerRpcWriterException(rpcException,requestId);
            }
        }finally {
            CONTEXT_LOCAL.set(null);
        }
    }

    private void handlerRpcWriterException(RpcException rpcException,int requestId){
        rpcClient.rpcDoneMap.remove(requestId);
        rpcContext.setThrowable(rpcException);
        subscriber.onError(rpcException);
        for (RpcClientAop aop : rpcClient.getAopList()) {
            aop.onResponseAfter(rpcContext);
        }
    }

    @Override
    public void cancel() {
        this.cancelFlag = true;
    }

    @Override
    public void subscribe(Subscriber<? super Object> subscriber) {
        this.subscriber = subscriber;
        CONTEXT_LOCAL.set(rpcContext);
        try {
            subscriber.onSubscribe(this);
        }finally {
            CONTEXT_LOCAL.set(null);
        }
    }

    public long getCurrentRequestCount() {
        return currentRequestCount;
    }
}
