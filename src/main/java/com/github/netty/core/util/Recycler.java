package com.github.netty.core.util;

import com.github.netty.core.constants.CoreConstants;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 回收者 (可以控制实例数量, 保证实例平稳, 不爆增,不爆减. 减少gc次数)
 *
 * 因为回收对象会频繁修改或清空内容, 建议加注解 @sun.misc.Contended.防止出现伪共享,刷新其他线程缓存, 该注解需设置 : -XX:-RestrictContended
 *
 * @author 84215
 */
public class Recycler<T> {

    /**
     * 当前对象的实例队列
     */
    private Queue<T> queue;
    /**
     * 当前对象的新建实例工厂
     */
    private Supplier<T> supplier;

    /**
     * 系统中所有的回收者
     */
    private static final List<Recycler> RECYCLER_LIST = new LinkedList<>();
    public static final AtomicInteger TOTAL_COUNT = new AtomicInteger();
    public static final AtomicInteger HIT_COUNT = new AtomicInteger();

    public Recycler(Supplier<T> supplier) {
        this(CoreConstants.getRecyclerCount(),supplier);
    }

    public Recycler(int instanceCount, Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
        this.queue = new Queue<>();
        RECYCLER_LIST.add(this);

        for(int i=0; i< instanceCount; i++) {
            recycleInstance(supplier.get());
        }
    }

    /**
     * 获取系统中所有的回收者列表
     * @return
     */
    public static List<Recycler> getRecyclerList() {
        return RECYCLER_LIST;
    }

    /**
     * 获取一个实例
     * @return
     */
    public T getInstance() {
//        return supplier.get();
        TOTAL_COUNT.incrementAndGet();
        T value = queue.pop();
        if(value == null){
            value = supplier.get();
        }else {
            HIT_COUNT.incrementAndGet();
        }
        return value;
    }

    /**
     * 回收实例
     * @param value
     */
    public void recycleInstance(T value) {
        queue.push(value);
    }

    /**
     * 实例的队列
     * @param <E>
     */
    private static class Queue<E> extends ConcurrentLinkedDeque<E> {
         @Override
         public void push(E e) {
             super.push(e);
         }

         @Override
         public E pop() {
             return super.pollFirst();
         }
     }
}
