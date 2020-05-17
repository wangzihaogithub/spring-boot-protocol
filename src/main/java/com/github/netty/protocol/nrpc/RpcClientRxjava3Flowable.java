package com.github.netty.protocol.nrpc;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Flowable;
import org.reactivestreams.Subscriber;

/**
 * support rxjava3 Flowable async response.
 * @author wangzihao
 *  2020/05/17/019
 */
public class RpcClientRxjava3Flowable extends Flowable<Object> {
    private final RpcClientReactivePublisher source;
    RpcClientRxjava3Flowable(RpcClientReactivePublisher source) {
        this.source = source;
    }

    @Override
    protected void subscribeActual(@NonNull Subscriber<? super Object> subscriber) {
        source.subscribe(subscriber);
    }

}
