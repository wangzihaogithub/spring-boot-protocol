package com.github.netty.nrpc.client;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.nrpc.api.HelloRequest;
import com.github.netty.nrpc.api.HelloResponse;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcClientAop;
import com.github.netty.protocol.nrpc.RpcContext;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping
public class HttpController {
    private final LoggerX logger = LoggerFactoryX.getLogger(getClass());
    @Autowired
    private HelloClient helloClient;
    @Autowired
    private HelloAsyncClient helloAsyncClient;

    @RequestMapping("/sayHello")
    public HelloResponse sayHello(String name){
        HelloRequest request = new HelloRequest();
        request.setId(1);
        request.setName("wang");
        HelloResponse helloResponse = helloClient.sayHello(name, 1, false,request);
        return helloResponse;
    }

    @RequestMapping("/sayHelloAsync")
    public DeferredResult<HelloResponse> sayHelloAsync(String name){
        DeferredResult<HelloResponse> deferredResult = new DeferredResult<>();
        Subscriber<HelloResponse> rpcHandler = new Subscriber<HelloResponse>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
                logger.info("onSubscribe");
            }

            @Override
            public void onNext(HelloResponse result) {
                RpcContext<RpcClient> rpcContext = RpcClientAop.CONTEXT_LOCAL.get();
                Object[] args = rpcContext.getArgs();
                String name = (String) args[0];
                int id = (int) args[1];
                logger.info("onNext = " + result);
                deferredResult.setResult(result);
            }

            @Override
            public void onError(Throwable t) {
                RpcContext<RpcClient> rpcContext = RpcClientAop.CONTEXT_LOCAL.get();
                long time = rpcContext.getRpcEndTimestamp() - rpcContext.getRpcBeginTimestamp();
                logger.error("time={}, onError = ",time, t.toString(),t);
                HelloResponse errorResult = new HelloResponse();
                errorResult.setSay("rpc error =" + t);
                deferredResult.setResult(errorResult);
            }

            @Override
            public void onComplete() {
                logger.info("onComplete");
            }
        };
        helloAsyncClient.sayHello(name,1).subscribe(rpcHandler);
        return deferredResult;
    }
}
