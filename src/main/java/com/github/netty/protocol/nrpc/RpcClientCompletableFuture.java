package com.github.netty.protocol.nrpc;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;

/**
 * support CompletableFuture async response.
 * @author wangzihao
 *  2020/05/30/019
 */
public class RpcClientCompletableFuture extends CompletableFuture<Object> {
    RpcClientCompletableFuture(RpcClientReactivePublisher source) {
        source.subscribe(new SubscriberAdapter(this));
    }

    private static class SubscriberAdapter implements Subscriber<Object>{
        private final CompletableFuture<Object> completableFuture;
        private Object result;
        private Throwable throwable;
        private SubscriberAdapter(CompletableFuture<Object> completableFuture) {
            this.completableFuture = completableFuture;
        }
        @Override
        public void onSubscribe(Subscription s) {
            s.request(1);
        }

        @Override
        public void onNext(Object o) {
            this.result = o;
        }

        @Override
        public void onError(Throwable t) {
            this.throwable = t;
        }

        @Override
        public void onComplete() {
            Throwable throwable = this.throwable;
            Object result = this.result;
            this.throwable = null;
            this.result = null;
            if(throwable != null) {
                completableFuture.completeExceptionally(throwable);
            }else {
                completableFuture.complete(result);
            }
        }
    }
}
