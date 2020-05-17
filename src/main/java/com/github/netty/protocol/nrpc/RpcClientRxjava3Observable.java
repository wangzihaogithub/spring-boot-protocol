package com.github.netty.protocol.nrpc;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * support rxjava3 Observable async response.
 * @author wangzihao
 *  2020/05/17/019
 */
public class RpcClientRxjava3Observable extends Observable<Object> {
    private final RpcClientReactivePublisher source;
    private static final Disposable EMPTY_DISPOSABLE = new Disposable() {
        @Override
        public void dispose() {
            // deliberately no-op
        }
        @Override
        public boolean isDisposed() {
            return true;
        }
    };

    RpcClientRxjava3Observable(RpcClientReactivePublisher source) {
        this.source = source;
    }

    @Override
    protected void subscribeActual(@NonNull Observer<? super Object> observer) {
        source.subscribe(new SubscriberAdapter(observer,EMPTY_DISPOSABLE));
    }

    private static class SubscriberAdapter implements Subscriber<Object>{
        private final Observer<? super Object> observer;
        private final Disposable disposable;
        private SubscriberAdapter(Observer<? super Object> observer,Disposable disposable) {
            this.observer = observer;
            this.disposable = disposable;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(1);
            observer.onSubscribe(disposable);
        }

        @Override
        public void onNext(Object o) {
            observer.onNext(o);
        }

        @Override
        public void onError(Throwable t) {
            observer.onError(t);
        }

        @Override
        public void onComplete() {
            observer.onComplete();
        }
    }
}
