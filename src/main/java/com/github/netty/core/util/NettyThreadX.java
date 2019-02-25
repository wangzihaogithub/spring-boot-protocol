package com.github.netty.core.util;

import io.netty.util.concurrent.FastThreadLocalThread;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by wangzihao on 2018/9/9/009.
 */
public class NettyThreadX extends FastThreadLocalThread {
    private List<Consumer<NettyThreadX>> threadStopListenerList;
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

    @Override
    public void run() {
        try {
            super.run();
        }finally {
            if(threadStopListenerList != null){
                for(Consumer<NettyThreadX> threadStopListener : threadStopListenerList) {
                    threadStopListener.accept(this);
                }
            }
        }
    }

    public void addThreadStopListener(Consumer<NettyThreadX> threadStopListener) {
        if(threadStopListenerList == null){
            threadStopListenerList = new ArrayList<>();
        }
        threadStopListenerList.add(threadStopListener);
    }
}
