package com.github.netty.core.util;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 回收者 (可以控制实例数量, 保证实例平稳, 不爆增,不爆减. 减少gc次数)
 *
 * 因为回收对象会频繁修改或清空内容, 建议加注解 @sun.misc.Contended.防止出现伪共享,刷新其他线程缓存, 该注解需设置 : -XX:-RestrictContended
 *
 * @author 84215
 */
public abstract class AbstractRecycler<T>   {

    /**
     * 系统中所有的回收者列表
     */
    private static final List<AbstractRecycler> RECYCLER_LIST = new LinkedList<>();
    /**
     * 当前对象的实例队列
     */
    private Queue<T> queue;
    /**
     * 系统中所有的回收者列表(为了断点时候方便看)
     */
    private List<AbstractRecycler> recyclerList;

    public static final AtomicInteger TOTAL_COUNT = new AtomicInteger();
    public static final AtomicInteger HIT_COUNT = new AtomicInteger();

    public AbstractRecycler() {
        this(15);
    }

    public AbstractRecycler(int instanceCount) {
        this.queue = new Queue<>();
        RECYCLER_LIST.add(this);
        recyclerList = RECYCLER_LIST;

        for(int i=0; i< instanceCount; i++) {
            recycleInstance(newInstance());
        }
    }

    /**
     * 获取系统中所有的回收者列表
     * @return
     */
    public static List<AbstractRecycler> getRecyclerList() {
        return RECYCLER_LIST;
    }

    /**
     * 新建实例
     * @return
     */
    protected abstract T newInstance();

    /**
     * 获取一个实例
     * @return
     */
    public T getInstance() {
//        return newInstance();
        TOTAL_COUNT.incrementAndGet();
        T value = queue.pop();
        if(value == null){
            value = newInstance();
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
    private class Queue<E> extends ConcurrentLinkedDeque<E> {
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
