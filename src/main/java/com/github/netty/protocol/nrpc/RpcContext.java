package com.github.netty.protocol.nrpc;

import com.github.netty.core.util.Recyclable;

/**
 * rpc context
 * @author wangzihao
 */
public class RpcContext<INSTANCE> implements Recyclable {
    private RpcPacket.RequestPacket request;
    private RpcPacket.ResponsePacket response;
    private Object[] args;
    private Object result;
    private RpcMethod<INSTANCE> rpcMethod;
    private Throwable throwable;
    private State state = State.INIT;

    public State getState() {
        return state;
    }

    void setState(State state) {
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

    @Override
    public void recycle() {
        this.request = null;
        this.response = null;
        this.rpcMethod = null;
        this.result = null;
        this.args = null;
        this.throwable = null;
        this.state = State.INIT;
    }

    @Override
    public String toString() {
        return "RpcContext{" +
                "requestId=" + (request == null? null:request.getRequestId()) +
                ", state=" + state +
                '}';
    }

    public enum State{
        INIT,
        WRITE_ING,
        WRITE_FINISH,
        READ_ING,
        READ_FINISH
    }

}
