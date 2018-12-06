package com.github.netty.core.util;

import io.netty.util.concurrent.FastThreadLocalThread;

/**
 * Created by acer01 on 2018/9/9/009.
 */
public class NettyThreadX extends FastThreadLocalThread {

    public NettyThreadX() {
        super();
    }

    public NettyThreadX(Runnable target) {
        super(target);
    }

    public NettyThreadX(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public NettyThreadX(String name) {
        super(name);
    }

    public NettyThreadX(ThreadGroup group, String name) {
        super(group, name);
    }

    public NettyThreadX(Runnable target, String name) {
        super(target, name);
    }

    public NettyThreadX(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public NettyThreadX(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
    }
}
