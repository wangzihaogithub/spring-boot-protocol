package com.github.netty.core.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 定时过期Map 会自动过期删除
 * 常用场景 ： localCache
 * @author wangzihao
 */
public class ExpiryLRUMap<K, V> extends AbstractMap<K, V> {
    public static final Object NULL = new Object(){
        @Override
        public String toString() {
            return "ExpiryLRUMap.NULL";
        }
    };
    private static volatile ScheduledFuture<?> SCHEDULED_FUTURE;
    private static final TransferQueue<Node<?,?>> EXPIRY_NOTIFY_QUEUE = new LinkedTransferQueue<>();
    private static final Set<ExpiryLRUMap<?,?>> INSTANCE_SET = Collections.newSetFromMap(new WeakHashMap<>());
    private final transient LongAdder missCount = new LongAdder();
    private final transient LongAdder hitCount = new LongAdder();
    private final AtomicBoolean removeIfExpiryIngFlag = new AtomicBoolean(false);
    private final ConcurrentLinkedHashMap<K, Node<K,V>> map;
    /**
     * 默认过期时间 2分钟
     */
    private long defaultExpiryTime;
    private boolean replaceNullValueFlag = false;
    private transient Collection<V> values;
    private transient EntrySet entrySet;
    /*超过时间的 过期通知*/
    private transient volatile Consumer<Node<K,V>> onExpiryConsumer = this::onExpiry;
    /*超过上限的 淘汰通知*/
    private transient volatile Consumer<Node<K, V>> onEvictionConsumer = this::onEviction;

    public ExpiryLRUMap(){
        this(1000*60*2);
    }

    public ExpiryLRUMap(long defaultExpiryTime){
        this(512,Long.MAX_VALUE, defaultExpiryTime,null);
    }

    public ExpiryLRUMap(int initialCapacity, long maxCacheSize, long defaultExpiryTime, ConcurrentLinkedHashMap.Weigher<Node<K,V>> weigher){
        this.defaultExpiryTime = defaultExpiryTime < 0 ? -1 : defaultExpiryTime;
        this.map = new ConcurrentLinkedHashMap.Builder<K,Node<K,V>>()
                .initialCapacity(initialCapacity)
                .maximumWeightedCapacity(maxCacheSize)
                .weigher(weigher == null? ConcurrentLinkedHashMap.Weighers.singleton() : weigher)
                .listener((key, value) -> {
                    Consumer<Node<K,V>> onEvictionConsumer = ExpiryLRUMap.this.onEvictionConsumer;
                    if(onEvictionConsumer != null) {
                        onEvictionConsumer.accept(value);
                    }
                })
//                .catchup()
                .build();
        //init static block. thread scheduled.
        synchronized (INSTANCE_SET) {
            INSTANCE_SET.add(this);
            if(SCHEDULED_FUTURE == null){
                SCHEDULED_FUTURE = ExpiresScan.scheduleAtFixedRate();
            }
        }
    }
    public long weightedSize() {
        return map.weightedSize();
    }

    public long getMaxCacheSize() {
        return map.capacity();
    }

    public void setMaxCacheSize(long maxCacheSize) {
        this.map.setCapacity(maxCacheSize);
    }

    public static Set<ExpiryLRUMap> getInstanceSet(){
        return Collections.unmodifiableSet(INSTANCE_SET);
    }

    public static TransferQueue<Node<?,?>> getExpiryNotifyQueue(){
        return EXPIRY_NOTIFY_QUEUE;
    }

    public Consumer<Node<K, V>> getOnEvictionConsumer() {
        return onEvictionConsumer;
    }

    public void setOnEvictionConsumer(Consumer<Node<K, V>> onEvictionConsumer) {
        this.onEvictionConsumer = onEvictionConsumer;
    }

    public long getMissCount() {
        return missCount.sum();
    }

    public long getHitCount() {
        return hitCount.sum();
    }

    public void setReplaceNullValueFlag(boolean replaceNullValueFlag) {
        this.replaceNullValueFlag = replaceNullValueFlag;
    }

    public Consumer<Node<K, V>> getOnExpiryConsumer() {
        return onExpiryConsumer;
    }

    public void setOnExpiryConsumer(Consumer<Node<K, V>> onExpiryConsumer) {
        this.onExpiryConsumer = onExpiryConsumer;
    }

    public boolean isReplaceNullValueFlag() {
        return replaceNullValueFlag;
    }

    @Override
    public V put(K key, V value) {
        return put(key,value,defaultExpiryTime);
    }

    /**
     * @param key key
     * @param value value
     * @param expiryTime 键值对有效期 毫秒(Long.MAX_VALUE 表示永不过期)
     * @return 旧值
     */
    public V put(K key, V value, long expiryTime) {
        if(value == null){
            value = (V) NULL;
        }
        Node<K,V> old;
        old = map.put(key, new Node<>(expiryTime,key, value,this));
        if(old == null){
            return null;
        }
        return old.getDataIfExpiry();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        for (Node<K, V> node : map.values()) {
            if(Objects.equals(node.getData(),value)){
                return true;
            }
        }
        return false;
    }

    @Override
    public V remove(Object key) {
        Node<K,V> old = map.remove(key);
        if(old == null){
            return null;
        }else {
            return old.getData();
        }
    }

    public V atomicGet(K key, Supplier<V> supplier) {
        /* todo atomicGet */
        Node<K,V> old = map.get(key);
        if(old == null){
            missCount.increment();
            V value = supplier.get();
            put(key,value);
            return value;
        }else {
            hitCount.increment();
            return old.getData();
        }
    }

    @Override
    public V get(Object key) {
        Node<K,V> old = map.get(key);
        if(old == null){
            missCount.increment();
            return null;
        }else {
            hitCount.increment();
            return old.getDataIfExpiry();
        }
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = values = new Values(map.values());
        }
        return vs;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K,V>> es = entrySet;
        if(entrySet == null){
            es = entrySet = new EntrySet(map.entrySet());
        }
        return es;
    }

    class Values extends AbstractCollection<V>{
        private Collection<Node<K,V>> values;
        Values(Collection<Node<K,V>> values) {
            this.values = values;
        }
        @Override
        public Iterator<V> iterator() {
            return new Iterator<V>() {
                private Iterator<Node<K,V>> iterator = values.iterator();
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public V next() {
                    return iterator.next().getDataIfExpiry();
                }

                @Override
                public void remove() {
                    iterator.remove();
                }
            };
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    class EntrySet extends AbstractSet<Entry<K,V>>{
        private Set<Entry<K, Node<K,V>>> entries;
        EntrySet(Set<Entry<K, Node<K,V>>> entries) {
            this.entries = entries;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new Iterator<Entry<K, V>>() {
                private Iterator<Entry<K, Node<K,V>>> iterator = entries.iterator();
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Entry<K, V> next() {
                    Entry<K, Node<K,V>> next = iterator.next();
                    K key = next.getKey();
                    return new Entry<K, V>() {

                        @Override
                        public K getKey() {
                            return key;
                        }

                        @Override
                        public V getValue() {
                            return next.getValue().getDataIfExpiry();
                        }

                        @Override
                        public V setValue(V value) {
                            Node<K,V> node = next.setValue(new Node<>(defaultExpiryTime, key,value, ExpiryLRUMap.this));
                            if(node == null){
                                return null;
                            }else {
                                return node.getDataIfExpiry();
                            }
                        }

                        @Override
                        public boolean equals(Object o) {
                            if (this == o) {
                                return true;
                            }
                            if (!(o instanceof Entry)) {
                                return false;
                            }
                            Entry node = (Entry) o;
                            return Objects.equals(getKey(), node.getKey()) &&
                                    Objects.equals(getValue(), node.getValue());
                        }

                        @Override
                        public int hashCode() {
                            return Objects.hash(getKey(), getValue());
                        }
                    };
                }

                @Override
                public void remove() {
                    iterator.remove();
                }
            };
        }

        @Override
        public int size() {
            return entries.size();
        }
    }

    public boolean removeIfExpiry(){
        return removeIfExpiry(EXPIRY_NOTIFY_QUEUE::offer);
    }

    public boolean removeIfExpiry(Consumer<Node<K,V>> removeConsumer){
        if(map.isEmpty()){
            return false;
        }
        //去掉多线程下, 多余的执行.
        if(removeIfExpiryIngFlag.compareAndSet(false,true)){
            try {
                boolean remove = false;
                Iterator<Entry<K, Node<K,V>>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Entry<K, Node<K, V>> next = iterator.next();
                    Node<K, V> value = next.getValue();
                    if (value.isExpiry()) {
                        iterator.remove();
                        removeConsumer.accept(value);
                        remove = true;
                    }
                }
                return remove;
            }finally {
                removeIfExpiryIngFlag.set(false);
            }
        }else {
            return false;
        }
    }

    public long getDefaultExpiryTime() {
        return defaultExpiryTime;
    }

    public void setDefaultExpiryTime(long defaultExpiryTime) {
        this.defaultExpiryTime = defaultExpiryTime;
    }

    public static boolean isExpiry(Node node) {
        if(node.expiryTimestamp == Long.MAX_VALUE){
            return false;
        }
        long currentTime = System.currentTimeMillis();
        return currentTime > node.expiryTimestamp;
    }

    @Override
    public String toString() {
        synchronized (map) {
            Iterator<Entry<K,Node<K,V>>> i = map.entrySet().iterator();
            if (! i.hasNext()) {
                return "{}";
            }
            long currentTime = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            for (; ; ) {
                Entry<K, Node<K, V>> e = i.next();
                K key = e.getKey();
                Node<K, V> node = e.getValue();
                V value = node.getDataIfExpiry();
                long timeout = node.expiryTimestamp;
                sb.append(key == this ? "(this Map)" : key);
                sb.append('=');
                sb.append(value == this ? "(this Map)" : value);

                sb.append('|');
                sb.append((timeout - currentTime) / 1000);
                sb.append("/s");

                if (!i.hasNext()) {
                    return sb.append('}').toString();
                }
                sb.append(',').append(' ');
            }
        }
    }

    public void onExpiry(Node<K,V> node){

    }

    public void onEviction(Node<K,V> node){

    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    public static class Node<KEY,VALUE> {
        private final ExpiryLRUMap<KEY,VALUE> expiryLRUMap;
        private final long createTimestamp = System.currentTimeMillis();
        private final long expiryTimestamp;
        private final VALUE data;
        private final KEY key;

        Node(long expiryTime, KEY key, VALUE value, ExpiryLRUMap<KEY, VALUE> expiryLRUMap) {
            long expiryTimestamp;
            if(expiryTime == Long.MAX_VALUE){
                expiryTimestamp = Long.MAX_VALUE;
            }else {
                expiryTimestamp = System.currentTimeMillis() + expiryTime;
                //如果算数溢出
                if(expiryTimestamp < 0){
                    expiryTimestamp = Long.MAX_VALUE;
                }
            }
            this.expiryTimestamp = expiryTimestamp;
            this.key = key;
            this.data = value;
            this.expiryLRUMap = expiryLRUMap;
        }

        public ExpiryLRUMap<KEY, VALUE> getExpiryLRUMap() {
            return expiryLRUMap;
        }

        public VALUE getDataIfExpiry() {
            if(isExpiry()) {
                return null;
            }else {
                return getData();
            }
        }

        public VALUE getData() {
            if(expiryLRUMap.isReplaceNullValueFlag()){
                return this.data;
            }else {
                return this.data == NULL? null : this.data;
            }
        }

        public KEY getKey() {
            return key;
        }

        public long getExpiryTimestamp() {
            return expiryTimestamp;
        }

        public long getCreateTimestamp() {
            return createTimestamp;
        }

        public boolean isExpiry(){
            return ExpiryLRUMap.isExpiry(this);
        }

        @Override
        public String toString() {
            VALUE data = getData();
            return data == null ? "null" : data.toString();
        }
    }

    public static class ExpiresNotify extends Thread {
        @Override
        public void run() {
            while (true){
                ExpiryLRUMap.Node node;
                try {
                    node = EXPIRY_NOTIFY_QUEUE.take();
                } catch (InterruptedException e) {
                    return;
                }
                Consumer<Node> consumer = node.expiryLRUMap.onExpiryConsumer;
                if(consumer != null){
                    try {
                        consumer.accept(node);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static class ExpiresScan implements Runnable{
        public static final ExpiresNotify NOTIFY_INSTANCE = new ExpiresNotify();
        private static final ExpiresScan INSTANCE = new ExpiresScan();
        static final AtomicInteger INCR = new AtomicInteger();
        static final ScheduledExecutorService SCHEDULED = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "ExpiryLRUMap-ExpiresScan" + INCR.getAndIncrement());
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        static {
            NOTIFY_INSTANCE.setName("ExpiryLRUMap-ExpiresNotify");
            NOTIFY_INSTANCE.start();
        }

        static long getScheduleInterval(){
            String intervalMillisecond = System.getProperty("ExpiryLRUMap-ExpiresScan.interval");
            long intervalLong = 100;
            if(intervalMillisecond != null && !intervalMillisecond.isEmpty()){
                try {
                    intervalLong = Long.parseLong(intervalMillisecond);
                }catch (Exception e){
                    //skip
                }
            }
            return intervalLong;
        }

        public static ScheduledFuture<?> scheduleAtFixedRate(){
            long intervalLong = getScheduleInterval();
            return SCHEDULED.scheduleAtFixedRate(INSTANCE, intervalLong, intervalLong, TimeUnit.MICROSECONDS);
        }

        @Override
        public void run() {
            if(INSTANCE_SET.isEmpty()){
                synchronized (INSTANCE_SET) {
                    if(INSTANCE_SET.isEmpty()) {
                        ScheduledFuture<?> scheduledFuture = SCHEDULED_FUTURE;
                        scheduledFuture.cancel(false);
                        SCHEDULED_FUTURE = null;
                    }
                }
                return;
            }
            for (ExpiryLRUMap<?,?> expiryLRUMap : INSTANCE_SET) {
                if(expiryLRUMap == null){
                    continue;
                }
                try {
                    expiryLRUMap.removeIfExpiry(EXPIRY_NOTIFY_QUEUE::offer);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private static void localVarTest() throws InterruptedException {
        ExpiryLRUMap lruMap = new ExpiryLRUMap();
        lruMap.put("data","123",5000);
        System.out.println("初始化 new ExpiryLRUMap() set = " + ExpiryLRUMap.INSTANCE_SET);
        while (true) {
            Object aa = lruMap.get("data");
            System.out.println("data = " + aa);
            Thread.sleep(1000);
            if(aa == null){
                return;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("set = " + ExpiryLRUMap.INSTANCE_SET);
        localVarTest();
        System.out.println("gc 前 set = " + ExpiryLRUMap.INSTANCE_SET);
        System.gc();
        System.out.println("gc 后 set = " + ExpiryLRUMap.INSTANCE_SET);

        int i=0;
        ExpiryLRUMap<String,Object> expiryLRUMap = new ExpiryLRUMap<>(1,2,
                Integer.MAX_VALUE,null
        );
        expiryLRUMap.setOnEvictionConsumer(node -> {
            long expiry = node.getExpiryTimestamp() - node.getCreateTimestamp();
            long timeout = System.currentTimeMillis() - node.getCreateTimestamp();
            System.out.println("eviction event. expiry = " + expiry+", timeout="+timeout);
        });
        expiryLRUMap.setOnExpiryConsumer(node -> {
            long expiry = node.getExpiryTimestamp() - node.getCreateTimestamp();
            long timeout = System.currentTimeMillis() - node.getCreateTimestamp();
            System.out.println("expiry event. expiry = " + expiry+", timeout="+timeout);
        });
        while (i++ < 3) {
            expiryLRUMap.put(i+"",i,1000);
            Set<ExpiryLRUMap<?,?>> set = ExpiryLRUMap.INSTANCE_SET;
            System.out.println("set = " + ExpiryLRUMap.INSTANCE_SET);
        }
    }

}
