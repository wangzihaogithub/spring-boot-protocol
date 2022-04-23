package com.github.netty.protocol.nrpc;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;

/**
 * support CompletableFuture async response.
 *
 * @author wangzihao
 * 2020/05/30/019
 */
public class RpcClientCompletableFuture<COMPLETE_RESULT> extends CompletableFuture<COMPLETE_RESULT> {
    private Subscription subscription;

    RpcClientCompletableFuture(RpcClientReactivePublisher source) {
        source.subscribe(new SubscriberAdapter(this));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (subscription != null) {
            subscription.cancel();
        }
        return super.cancel(mayInterruptIfRunning);
    }

    public static class SubscriberAdapter<RESULT> implements Subscriber<RESULT> {
        private final RpcClientCompletableFuture<RESULT> completableFuture;
        private RESULT result;
        private Throwable throwable;

        private SubscriberAdapter(RpcClientCompletableFuture<RESULT> completableFuture) {
            this.completableFuture = completableFuture;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(1);
            completableFuture.subscription = s;
        }

        @Override
        public void onNext(RESULT o) {
            this.result = o;
        }

        @Override
        public void onError(Throwable t) {
            this.throwable = t;
        }

        @Override
        public void onComplete() {
            Throwable throwable = this.throwable;
            RESULT result = this.result;
            this.throwable = null;
            this.result = null;
            if (throwable != null) {
                completableFuture.completeExceptionally(throwable);
            } else {
                completableFuture.complete(result);
            }
        }
    }
}
