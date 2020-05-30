package com.github.netty.nrpc.client;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.nrpc.api.HelloDTO;
import com.github.netty.nrpc.api.HelloData;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcClientAop;
import com.github.netty.protocol.nrpc.RpcContext;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@RestController
@RequestMapping
public class HttpController {
    private final LoggerX logger = LoggerFactoryX.getLogger(getClass());
    @Autowired
    private HelloClient helloClient;
    @Autowired
    private HelloAsyncClient helloAsyncClient;
    @Autowired
    private HelloRxjava3AsyncClient helloRxjava3AsyncClient;

    @RequestMapping("/sayHello")
    public HelloData sayHello(String name){
        HelloDTO request = new HelloDTO();
        request.setId(1);
        request.setName("wang");
//        HelloData helloResponse = helloClient.sayHello(name, 1, false,request);
//        return helloResponse;
        return new HelloData();
    }

    @RequestMapping("/sayHelloAsync")
    public DeferredResult<HelloData> sayHelloAsync(String name) throws ExecutionException, InterruptedException {
        DeferredResult<HelloData> deferredResult = new DeferredResult<>();
        Subscriber<HelloData> rpcHandler = new Subscriber<HelloData>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
                logger.info("onSubscribe");
            }

            @Override
            public void onNext(HelloData result) {
                RpcContext<RpcClient> rpcContext = RpcClientAop.CONTEXT_LOCAL.get();
                Object[] args = rpcContext.getArgs();
                String name = (String) args[0];
                int id = (int) args[1];
                logger.info("onNext = " + result);
                deferredResult.setResult(result);
            }

            @Override
            public void onError(Throwable t) {
                deferredResult.setErrorResult(t);
            }

            @Override
            public void onComplete() {
                logger.info("onComplete");
            }
        };

        helloAsyncClient.sayHelloByTest(name,1).subscribe(rpcHandler);
        HelloData helloData = helloAsyncClient.sayHello1(name, 1).get();

        helloAsyncClient.sayHello1(name,1).thenAcceptAsync( data->{
            logger.info(" data = " + data);
        }, command -> {
            logger.info(" command = " + command);
            command.run();
        });
        return deferredResult;
    }

    @RequestMapping("/sayHelloRxjava3Async")
    public DeferredResult<HelloData> sayHelloRxjava3Async(String name){
        DeferredResult<HelloData> deferredResult = new DeferredResult<>();
        helloRxjava3AsyncClient.sayHello(name, 1)
                .subscribe(
                        deferredResult::setResult,
                        deferredResult::setErrorResult);

        helloRxjava3AsyncClient.sayHello1("a1")
                .subscribe(
                        helloResponse -> System.out.println("helloResponse = " + helloResponse),
                        throwable -> System.out.println("throwable = " + throwable));

        helloRxjava3AsyncClient.sayHello1("a2")
                .blockingSubscribe(
                        helloResponse -> System.out.println("helloResponse = " + helloResponse),
                        throwable -> System.out.println("throwable = " + throwable));
        return deferredResult;
    }
}
