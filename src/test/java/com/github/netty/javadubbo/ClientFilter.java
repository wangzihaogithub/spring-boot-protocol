package com.github.netty.javadubbo;

import org.apache.dubbo.rpc.*;

public class ClientFilter implements org.apache.dubbo.rpc.Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        invoker.getUrl().addParameter("proxy-app", "order-service");
        return invoker.invoke(invocation);
    }
}
