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
    private Exception exception;

    public Object[] getArgs() {
        return args;
    }

    void setArgs(Object[] args) {
        this.args = args;
    }

    public Exception getException() {
        return exception;
    }

    void setException(Exception exception) {
        this.exception = exception;
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
        this.exception = null;
    }
}
