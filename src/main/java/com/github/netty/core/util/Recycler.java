package com.github.netty.core.util;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Collector (can control the number of instances, ensure the stability of instances, no explosion, no explosion, reduce the number of gc)
 * @author wangzihao
 */
public class Recycler<T> {
    private static final int DEFAULT_INSTANCE_COUNT = SystemPropertyUtil.getInt("netty-core.recyclerCount",2);
    private static final boolean ENABLE = SystemPropertyUtil.getBoolean("netty-core.recyclerEnable",true);
    private final int instanceCount;
    /**
     * The instance queue of the current object
     */
    private final FastThreadLocal<Queue<T>> queue = new FastThreadLocal<Queue<T>>(){
        @Override
        protected Queue<T> initialValue() throws Exception {
            return PlatformDependent.newFixedMpscQueue(instanceCount);
        }
    };
    /**
     * New instance factory for the current object
     */
    private Supplier<T> supplier;

    /**
     * All recyclers
     */
    private static final List<Recycler> RECYCLER_LIST = new ArrayList<>();
    public static final LongAdder HIT_COUNT = new LongAdder();
    public static final LongAdder MISS_COUNT = new LongAdder();

//    private StackTraceElement formStack;
    private Thread formThread;

    public Recycler(Supplier<T> supplier) {
        this(DEFAULT_INSTANCE_COUNT,supplier);
    }

    public Recycler(int instanceCount, Supplier<T> supplier) {
        this.instanceCount = instanceCount;
        this.supplier = supplier;
        RECYCLER_LIST.add(this);
        this.formThread = Thread.currentThread();
//        this.formStack = formThread.getStackTrace()[3];
    }

    /**
     * Gets a list of all recyclers
     * @return List
     */
    public static List<Recycler> getRecyclerList() {
        return RECYCLER_LIST;
    }

    /**
     * Get an instance
     * @return object
     */
    public T getInstance() {
        if(ENABLE) {
            T value = queue.get().poll();
            if (value == null) {
                value = supplier.get();
                MISS_COUNT.increment();
            }else {
                HIT_COUNT.increment();
            }
            return value;
        }else {
            return supplier.get();
        }
    }

    /**
     * Recycling instance
     * @param value value
     */
    public void recycleInstance(T value) {
        queue.get().offer(value);
    }


    @Override
    public String toString() {
        return "Recycler{" +
                "size=" + queue.get().size() +
//                ", formStack=" + StringUtil.simpleClassName(formStack.getClassName()) +
                ", formThread=" + formThread +
                '}';
    }

    public static void main(String[] args) throws InterruptedException {
        int count = 10000;
        Recycler<Date> recycler = new Recycler<>(()->{
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return new Date();
        });


        ThreadPoolX threadPool = new ThreadPoolX("Test", 16);
        long begin = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            threadPool.execute(() -> {
                Date instance = recycler.getInstance();
                recycler.recycleInstance(instance);
                latch.countDown();
            });
        }
        latch.await();

        long time = System.currentTimeMillis() - begin;
        System.out.printf("time = %d/ms, hit = %s/c, mis = %s/c\n", time, Recycler.HIT_COUNT, Recycler.MISS_COUNT);
    }
}
