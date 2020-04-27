package com.github.netty.core.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 定时过期Map 会自动过期删除
 * 常用场景 ： localCache
 * @author acer01
 */
public class ExpiryLRUMap<K, V> extends AbstractMap<K, V> {
    public static final Object NULL = new Object();
    private final Map<K, Node<K,V>> map;
    private final AtomicBoolean removeIfExpiryIngFlag = new AtomicBoolean(false);
    private boolean replaceNullValueFlag = false;
    private static final TransferQueue<ExpiryLRUMap.Node<?,?>> EXPIRY_NOTIFY_QUEUE = new LinkedTransferQueue<>();
    private static final Set<ExpiryLRUMap<?,?>> INSTANCE_SET = Collections.newSetFromMap(new WeakHashMap<>());
    /**
     * 默认过期时间 2分钟
     */
    private long defaultExpiryTime;
    private transient Collection<V> values;
    private transient EntrySet entrySet;
    private transient Function<Entry<K,Node<K,V>>,Boolean> removeEldestEntryFunction = this::removeEldestEntry;
    private transient Consumer<Node<K,V>> onExpiryConsumer = this::onExpiry;
    private final transient LongAdder missCount = new LongAdder();
    private final transient LongAdder hitCount = new LongAdder();

    public ExpiryLRUMap(){
        this(512,0.75F,true,1000*60*2);
    }

    public ExpiryLRUMap(long defaultExpiryTime){
        this(512,0.75F,true,defaultExpiryTime);
    }

    public ExpiryLRUMap(int initialCapacity, float loadFactor, boolean accessOrder, long defaultExpiryTime){
        this.defaultExpiryTime = defaultExpiryTime < 0 ? -1 : defaultExpiryTime;
        this.map = new LinkedHashMap<K, Node<K,V>>(initialCapacity,loadFactor,accessOrder){
            @Override
            protected boolean removeEldestEntry(Map.Entry<K,Node<K,V>> eldest) {
                if(removeEldestEntryFunction != null){
                    return removeEldestEntryFunction.apply(eldest);
                }else {
                    return false;
                }
            }
        };
        //init static block. thread scheduled.
        synchronized (INSTANCE_SET) {
            INSTANCE_SET.add(this);
            if(ExpiresScan.SCHEDULED_FUTURE == null){
                ExpiresScan.scheduleAtFixedRate();
            }
        }
    }

    public static Set<ExpiryLRUMap> getInstanceSet(){
        return Collections.unmodifiableSet(INSTANCE_SET);
    }

    public static TransferQueue<ExpiryLRUMap.Node<?,?>> getExpiryNotifyQueue(){
        return EXPIRY_NOTIFY_QUEUE;
    }

    public long getMissCount() {
        return missCount.sum();
    }

    public long getHitCount() {
        return hitCount.sum();
    }

    public void setRemoveEldestEntryFunction(Function<Entry<K, Node<K,V>>, Boolean> removeEldestEntryFunction) {
        this.removeEldestEntryFunction = removeEldestEntryFunction;
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

    protected boolean removeEldestEntry(Entry<K, Node<K,V>> eldest) {
        return false;
    }

    @Override
    public V put(K key, V value) {
        return put(key,value,defaultExpiryTime);
    }

    /**
     * @param key
     * @param value
     * @param expiryTime 键值对有效期 毫秒(Long.MAX_VALUE 表示永不过期)
     * @return
     */
    public V put(K key, V value, long expiryTime) {
        if(replaceNullValueFlag && value == null){
            value = (V) NULL;
        }
        Node<K,V> old;
        synchronized (map) {
            old = map.put(key, new Node<>(expiryTime,key, value,this));
//            removeIfExpiry();
        }
        if(old == null){
            return null;
        }
        return old.getDataIfExpiry();
    }

    @Override
    public boolean containsKey(Object key) {
//        setNullIfExpiry();
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
//        setNullIfExpiry();
        return map.containsValue(value);
    }

    @Override
    public V remove(Object key) {
        Node<K,V> old;
        synchronized (map) {
            old = map.remove(key);
        }
        if(old == null){
            return null;
        }else {
            return old.getData();
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        synchronized (map) {
            map.remove(key);
            return true;
        }
    }

    public V atomicGet(K key, Supplier<V> supplier) {
        synchronized (map){
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
    }

    @Override
    public V get(Object key) {
//        setNullIfExpiry();
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
//        setNullIfExpiry();
        return map.keySet();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Collection<V> values() {
//        setNullIfExpiry();
        Collection<V> vs = values;
        if (vs == null) {
            vs = values = new Values(map.values());
        }
        return vs;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
//        setNullIfExpiry();
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
                            Node<K,V> node = next.setValue(new Node<>(defaultExpiryTime, key,value,ExpiryLRUMap.this));
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
                synchronized (map) {
                    boolean remove = false;
                    Iterator<Entry<K, Node<K,V>>> iterator = map.entrySet().iterator();
                    while (iterator.hasNext()){
                        Entry<K, Node<K,V>> next = iterator.next();
                        Node<K,V> value = next.getValue();
                        if(value.isExpiry()){
                            iterator.remove();
                            removeConsumer.accept(value);
                            remove = true;
                        }
                    }
                    return remove;
                }
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

    public boolean isExpiry(Node node) {
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

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
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
        ExpiryLRUMap<String,Object> expiryLRUMap = new ExpiryLRUMap<>();
        expiryLRUMap.setOnExpiryConsumer(node -> {
            long expiry = node.getExpiryTimestamp() - node.getCreateTimestamp();
            long timeout = System.currentTimeMillis() - node.getCreateTimestamp();
            System.out.println("expiry event. expiry = " + expiry+", timeout="+timeout);
        });
        while (i++ < 100) {
            expiryLRUMap.put(i+"",i,1000);
            Set<ExpiryLRUMap<?,?>> set = ExpiryLRUMap.INSTANCE_SET;
            System.out.println("set = " + ExpiryLRUMap.INSTANCE_SET);
            Thread.sleep(1000);
        }
    }

    public static class Node<KEY,VALUE> {
        private final ExpiryLRUMap<KEY,VALUE> expiryLRUMap;
        private final long createTimestamp = System.currentTimeMillis();
        private final long expiryTimestamp;
        private final VALUE data;
        private final KEY key;

        Node(long expiryTime, KEY key,VALUE value,ExpiryLRUMap<KEY, VALUE> expiryLRUMap) {
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
                return this.data;
            }
        }

        public VALUE getData() {
            return data;
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
            return expiryLRUMap.isExpiry(this);
        }

        @Override
        public String toString() {
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
                Consumer<ExpiryLRUMap.Node> consumer = node.expiryLRUMap.onExpiryConsumer;
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
        static final ExpiresScan INSTANCE = new ExpiresScan();
        public static final ExpiresNotify NOTIFY_INSTANCE = new ExpiresNotify();
        public static ScheduledFuture<?> SCHEDULED_FUTURE;
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

        static void scheduleAtFixedRate(){
            long intervalLong = getScheduleInterval();
            SCHEDULED_FUTURE = SCHEDULED.scheduleAtFixedRate(INSTANCE, intervalLong, intervalLong, TimeUnit.MICROSECONDS);
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

}
