package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.Recyclable;

import java.net.InetSocketAddress;

/**
 * rpc context
 *
 * @author wangzihao
 */
public class RpcContext<INSTANCE> implements Recyclable {
    private InetSocketAddress remoteAddress;
    private InetSocketAddress localAddress;
    private RpcPacket.RequestPacket request;
    private RpcPacket.ResponsePacket response;
    private Object[] args;
    private Object result;
    private RpcMethod<INSTANCE> rpcMethod;
    private Throwable throwable;
    private State state;
    private long rpcBeginTimestamp;
    private long rpcEndTimestamp;
    private int timeout;

    public int getTimeout() {
        return timeout;
    }

    void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public long getRpcBeginTimestamp() {
        return rpcBeginTimestamp;
    }

    public void setRpcBeginTimestamp(long rpcBeginTimestamp) {
        this.rpcBeginTimestamp = rpcBeginTimestamp;
    }

    public long getRpcEndTimestamp() {
        return rpcEndTimestamp;
    }

    public void setRpcEndTimestamp(long rpcEndTimestamp) {
        this.rpcEndTimestamp = rpcEndTimestamp;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    void setRemoteAddress(InetSocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    void setLocalAddress(InetSocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Object[] getArgs() {
        return args;
    }

    void setArgs(Object[] args) {
        this.args = args;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public RpcPacket.RequestPacket getRequest() {
        return request;
    }

    void setRequest(RpcPacket.RequestPacket request) {
        this.request = request;
    }

    public RpcPacket.ResponsePacket getResponse() {
        return response;
    }

    void setResponse(RpcPacket.ResponsePacket response) {
        this.response = response;
    }

    public Object getResult() {
        return result;
    }

    void setResult(Object result) {
        this.result = result;
    }

    public RpcMethod<INSTANCE> getRpcMethod() {
        return rpcMethod;
    }

    void setRpcMethod(RpcMethod<INSTANCE> rpcMethod) {
        this.rpcMethod = rpcMethod;
    }

    public boolean isInnerMethod() {
        return rpcMethod.isInnerMethodFlag();
    }

    @Override
    public void recycle() {
        this.request = null;
        this.response = null;
        this.rpcMethod = null;
        this.result = null;
        this.args = null;
        this.throwable = null;
        this.localAddress = null;
        this.remoteAddress = null;
        this.state = null;
    }

    @Override
    public String toString() {
        return "RpcContext{" +
                "requestId=" + (request == null ? null : request.getRequestId()) +
                ", state=" + state +
                '}';
    }

    public enum RpcState implements State {
        INIT(false),

        WRITE_ING(false),
        WRITE_CHUNK(false),
        WRITE_FINISH(false),

        READ_ING(false),
        READ_CHUNK(false),
        READ_FINISH(false),

        END(true),
        TIMEOUT(true);

        private final boolean complete;

        RpcState(boolean complete) {
            this.complete = complete;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }
    }

}
