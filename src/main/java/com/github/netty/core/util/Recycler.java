package com.github.netty.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Collector (can control the number of instances, ensure the stability of instances, no explosion, no explosion, reduce the number of gc)
 * @author wangzihao
 */
public class Recycler<T> {
    private static final int DEFAULT_INSTANCE_COUNT = SystemPropertyUtil.getInt("netty-core.recyclerCount",30);
    private static final boolean ENABLE = SystemPropertyUtil.getBoolean("netty-core.recyclerEnable",true);
    /**
     * The instance queue of the current object
     */
    private Queue<T> queue;
    /**
     * New instance factory for the current object
     */
    private Supplier<T> supplier;

    /**
     * All recyclers
     */
    private static final List<Recycler> RECYCLER_LIST = new ArrayList<>();
    public static final LongAdder TOTAL_COUNT = new LongAdder();
    public static final LongAdder HIT_COUNT = new LongAdder();

//    private StackTraceElement formStack;
    private Thread formThread;

    public Recycler(Supplier<T> supplier) {
        this(DEFAULT_INSTANCE_COUNT,supplier);
    }

    public Recycler(int instanceCount, Supplier<T> supplier) {
        this.supplier = supplier;
        this.queue = new Queue<>();
        RECYCLER_LIST.add(this);
        this.formThread = Thread.currentThread();
//        this.formStack = formThread.getStackTrace()[3];

        if(ENABLE) {
            for (int i = 0; i < instanceCount; i++) {
                recycleInstance(supplier.get());
            }
        }
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
            TOTAL_COUNT.increment();
            T value = queue.pop();
            if (value == null) {
                value = supplier.get();
            } else {
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
        queue.push(value);
    }


    @Override
    public String toString() {
        return "Recycler{" +
                "size=" + queue.size() +
//                ", formStack=" + StringUtil.simpleClassName(formStack.getClassName()) +
                ", formThread=" + formThread +
                '}';
    }

    /**
     * Queue of instances
     * @param <E> type
     */
    private static class Queue<E> extends ConcurrentLinkedQueue<E> {
         public void push(E e) {
             super.offer(e);
         }

         public E pop() {
             return super.poll();
         }
     }
}
