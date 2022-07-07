package com.github.netty.core.util;

import java.lang.ref.Reference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.*;

/**
 * 定时过期Map 会自动过期删除
 * <p>
 * 支持项 ：    1.定时过期(过期事件通知) {@link #setOnExpiryConsumer(Consumer)} {@link #onExpiry(Node)}
 * 2. LRU淘汰机制(淘汰事件通知) {@link #setOnEvictionConsumer(Consumer)} {@link #onEviction(Node)}
 * 3. Map操作
 * 4. 并发操作(线程安全) {@link ConcurrentMap}
 * 5. gc回收 Reference(Weak,Soft,strong). {@link #ExpiryLRUMap(int, long, long, ConcurrentLinkedHashMap.Weigher, Class)}
 * 6. 统计功能(miss, hit) {@link #getHitCount()} {@link #getMissCount()}
 * 7. null值替换, 防止缓存击穿 {@link #setReplaceNullValueFlag(boolean)} {@link #NULL} if(data == ExpiryLRUMap.NULL)
 * <p>
 * 常用场景 ： localCache
 *
 * @author wangzihao
 */
public class ExpiryLRUMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "ExpiryLRUMap.NULL";
        }
    };
    private static final ConcurrentSkipListSet<Node> NO_EXPIRY_NODES = new ConcurrentSkipListSet<>((o1, o2) -> {
        if (o1 == o2) {
            return 0;
        }
        long x = o1.getExpiryTimestamp();
        long y = o2.getExpiryTimestamp();
        return x <= y ? -1 : 1;
    });
    private static final BlockingQueue<Node<?, ?>> EXPIRY_NOTIFY_QUEUE = new LinkedBlockingQueue<>();
    private static final Set<ExpiryLRUMap<?, ?>> INSTANCE_SET = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile ScheduledFuture<?> SCHEDULED_FUTURE;
    private final transient LongAdder missCount = new LongAdder();
    private final transient LongAdder hitCount = new LongAdder();
    private final ConcurrentLinkedHashMap<K, Node<K, V>> map;
    private long defaultExpiryTime;
    /**
     * null值替换, 防止缓存提击穿. 需要设置成true后, 取值后需要判断是否 data == ExpiryLRUMap.NULL
     */
    private boolean replaceNullValueFlag = false;
    private transient Collection<V> values;
    private transient EntrySet entrySet;
    /**
     * 超过时间的 过期通知
     */
    private transient volatile Consumer<Node<K, V>> onExpiryConsumer = this::onExpiry;
    /**
     * 超过上限的 淘汰通知
     */
    private transient volatile Consumer<Node<K, V>> onEvictionConsumer = this::onEviction;
    /**
     * 用户主动删除 删除通知
     */
    private transient volatile Consumer<Node<K, V>> onRemoveConsumer = this::onRemove;

    /**
     * 默认永不过期 (相当于普通的 ConcurrentMap)
     */
    public ExpiryLRUMap() {
        this(Long.MAX_VALUE);
    }

    public ExpiryLRUMap(long defaultExpiryTime) {
        this(256, Long.MAX_VALUE, defaultExpiryTime, null);
    }

    public ExpiryLRUMap(int initialCapacity, long maxCacheSize, long defaultExpiryTime, ConcurrentLinkedHashMap.Weigher<Node<K, V>> weigher) {
        this(initialCapacity, maxCacheSize, defaultExpiryTime, weigher, null);
    }

    /**
     * @param initialCapacity   initialCapacity
     * @param maxCacheSize      maxCacheSize
     * @param defaultExpiryTime defaultExpiryTime
     * @param weigher           weigher
     * @param referenceType     null is FinalReference.
     *                          else if {@link java.lang.ref.WeakReference}
     *                          else if {@link java.lang.ref.SoftReference}
     */
    public ExpiryLRUMap(int initialCapacity, long maxCacheSize, long defaultExpiryTime, ConcurrentLinkedHashMap.Weigher<Node<K, V>> weigher, Class<? extends Reference> referenceType) {
        this.defaultExpiryTime = defaultExpiryTime < 0 ? -1 : defaultExpiryTime;
        this.map = new ConcurrentLinkedHashMap.Builder<K, Node<K, V>>()
                .initialCapacity(initialCapacity)
                .maximumWeightedCapacity(maxCacheSize)
                .referenceType(referenceType)
                .weigher(weigher == null ? ConcurrentLinkedHashMap.Weighers.singleton() : weigher)
                .listener((key, value) -> {
                    Consumer<Node<K, V>> onEvictionConsumer = ExpiryLRUMap.this.onEvictionConsumer;
                    if (onEvictionConsumer != null) {
                        onEvictionConsumer.accept(value);
                    }
                })
//                .catchup()
                .build();
        //init static block. thread scheduled.
        synchronized (INSTANCE_SET) {
            INSTANCE_SET.add(this);
            if (SCHEDULED_FUTURE == null) {
                SCHEDULED_FUTURE = ExpiresScan.scheduleWithFixedDelay();
            }
        }
    }

    public static Set<ExpiryLRUMap> getInstanceSet() {
        return Collections.unmodifiableSet(INSTANCE_SET);
    }

    public static BlockingQueue<Node<?, ?>> getExpiryNotifyQueue() {
        return EXPIRY_NOTIFY_QUEUE;
    }

    public static boolean isExpiry(Node node) {
        if (node.expiryTimestamp == Long.MAX_VALUE) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        return currentTime > node.expiryTimestamp;
    }

    private static void localVarTest() throws InterruptedException {
        ExpiryLRUMap lruMap = new ExpiryLRUMap();
        lruMap.put("data3", "1233", -1);
        lruMap.put("data", "123", 5000);
        System.out.println("初始化 new ExpiryLRUMap() set = " + ExpiryLRUMap.INSTANCE_SET);
        while (true) {
            Object aa = lruMap.get("data");
            System.out.println("data = " + aa);
            Thread.sleep(1000);
            if (aa == null) {
                return;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {

        System.out.println("set = " + ExpiryLRUMap.INSTANCE_SET);
//        localVarTest();
        System.out.println("gc 前 set = " + ExpiryLRUMap.INSTANCE_SET);
        System.gc();
        System.out.println("gc 后 set = " + ExpiryLRUMap.INSTANCE_SET);

        int i = 0;
        ExpiryLRUMap<String, Object> expiryLRUMap = new ExpiryLRUMap<>(1, 3333,
                Integer.MAX_VALUE, null
        );
        for (int j = 0; j < 100; j++) {
            expiryLRUMap.put(j + "", j);
        }
        for (int j = 1000; j > 0; j--) {
            expiryLRUMap.put(j + "", j);
        }
        System.out.println("expiryLRUMap = " + expiryLRUMap);

        expiryLRUMap.setOnEvictionConsumer(node -> {
            long expiry = node.getExpiryTimestamp() - node.getCreateTimestamp();
            long timeout = System.currentTimeMillis() - node.getCreateTimestamp();
            System.out.println("eviction event. expiry = " + expiry + ", timeout=" + timeout);
        });
        expiryLRUMap.setOnExpiryConsumer(node -> {
            long expiry = node.getExpiryTimestamp() - node.getCreateTimestamp();
            long timeout = System.currentTimeMillis() - node.getCreateTimestamp();
            System.out.println("expiry event. expiry = " + expiry + ", timeout=" + timeout);
        });
        while (i++ < 3) {
            expiryLRUMap.put(i + "", i, 1000);
            Set<ExpiryLRUMap<?, ?>> set = ExpiryLRUMap.INSTANCE_SET;
            System.out.println("set = " + ExpiryLRUMap.INSTANCE_SET);
        }
        System.gc();
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

    public Consumer<Node<K, V>> getOnExpiryConsumer() {
        return onExpiryConsumer;
    }

    public void setOnExpiryConsumer(Consumer<Node<K, V>> onExpiryConsumer) {
        this.onExpiryConsumer = onExpiryConsumer;
    }

    public void setOnRemoveConsumer(Consumer<Node<K, V>> onRemoveConsumer) {
        this.onRemoveConsumer = onRemoveConsumer;
    }

    public boolean isReplaceNullValueFlag() {
        return replaceNullValueFlag;
    }

    public void setReplaceNullValueFlag(boolean replaceNullValueFlag) {
        this.replaceNullValueFlag = replaceNullValueFlag;
    }

    @Override
    public V put(K key, V value) {
        return put(key, value, defaultExpiryTime);
    }

    /**
     * @param key     key
     * @param value   value
     * @param timeout 键值对有效期 毫秒(Long.MAX_VALUE 表示永不过期)
     * @return 旧值
     */
    public V put(K key, V value, long timeout) {
        if (value == null) {
            value = (V) NULL;
        }
        Node<K, V> old = map.get(key);
        Node<K, V> node = new Node<>(timeout, key, value, this);
        if (old != null) {
            synchronized (old) {
                old.covered = true;
                map.put(key, node);
                return old.getData();
            }
        } else {
            map.put(key, node);
            return null;
        }
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
            if (Objects.equals(node.getData(), value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V remove(Object key) {
        Node<K, V> old = map.remove(key);
        if (old == null) {
            return null;
        } else {
            notifyRemove(old);
            return old.getData();
        }
    }

    public void notifyRemove(Node<K, V> node) {
        Consumer<Node<K, V>> onRemoveConsumer = this.onRemoveConsumer;
        if (onRemoveConsumer != null) {
            onRemoveConsumer.accept(node);
        }
    }

    public V atomicGet(K key, Supplier<V> supplier) {
        /* todo atomicGet */
        Node<K, V> old = map.get(key);
        if (old == null) {
            missCount.increment();
            V value = supplier.get();
            put(key, value);
            return value;
        } else {
            hitCount.increment();
            return old.getData();
        }
    }

    @Override
    public V get(Object key) {
        Node<K, V> old = map.get(key);
        if (old == null) {
            missCount.increment();
            return null;
        } else {
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
        Set<Entry<K, V>> es = entrySet;
        if (entrySet == null) {
            es = entrySet = new EntrySet(map.entrySet());
        }
        return es;
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        V v;
        return ((v = get(key)) != null) ? v : defaultValue;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        for (Entry<K, V> entry : entrySet()) {
            K k;
            V v;
            try {
                k = entry.getKey();
                v = entry.getValue();
            } catch (IllegalStateException ise) {
                // this usually means the entry is no longer in the map.
                continue;
            }
            action.accept(k, v);
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        Node<K, V> old = map.get(key);
        if (old != null && Objects.equals(old.getData(), value)) {
            map.remove(key, old);
            notifyRemove(old);
            return true;
        }
        return false;
    }

    @Override
    public V replace(K key, V newValue) {
        Node<K, V> old = map.get(key);
        if (old != null) {
            map.put(key, new Node<>(old.expiryTime, key, newValue, this));
            return old.getData();
        }
        return null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Node<K, V> old = map.get(key);
        if (old != null && Objects.equals(old.getData(), oldValue)) {
            map.put(key, new Node<>(old.expiryTime, key, newValue, this));
            return true;
        }
        return false;
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        for (Entry<K, Node<K, V>> entry : map.entrySet()) {
            Node<K, V> old = entry.getValue();
            K key = entry.getKey();
            V value = old.getData();
            V newValue = function.apply(key, value);
            entry.setValue(new Node<>(old.expiryTime, key, newValue, this));
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V v, newValue;
        return ((v = get(key)) == null &&
                (newValue = mappingFunction.apply(key)) != null &&
                (v = putIfAbsent(key, newValue)) == null) ? newValue : v;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue;
        while ((oldValue = get(key)) != null) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                if (replace(key, oldValue, newValue)) {
                    return newValue;
                }
            } else if (remove(key, oldValue)) {
                return null;
            }
        }
        return oldValue;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue = get(key);
        for (; ; ) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue == null) {
                // delete mapping
                if (oldValue != null || containsKey(key)) {
                    // something to remove
                    if (remove(key, oldValue)) {
                        // removed the old value as expected
                        return null;
                    }

                    // some other value replaced old value. try again.
                    oldValue = get(key);
                } else {
                    // nothing to do. Leave things as they were.
                    return null;
                }
            } else {
                // add or replace old mapping
                if (oldValue != null) {
                    // replace
                    if (replace(key, oldValue, newValue)) {
                        // replaced as expected.
                        return newValue;
                    }

                    // some other value replaced old value. try again.
                    oldValue = get(key);
                } else {
                    // add (replace if oldValue was null)
                    if ((oldValue = putIfAbsent(key, newValue)) == null) {
                        // replaced
                        return newValue;
                    }

                    // some other value replaced old value. try again.
                }
            }
        }
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        Objects.requireNonNull(value);
        V oldValue = get(key);
        for (; ; ) {
            if (oldValue != null) {
                V newValue = remappingFunction.apply(oldValue, value);
                if (newValue != null) {
                    if (replace(key, oldValue, newValue)) {
                        return newValue;
                    }
                } else if (remove(key, oldValue)) {
                    return null;
                }
                oldValue = get(key);
            } else {
                if ((oldValue = putIfAbsent(key, value)) == null) {
                    return value;
                }
            }
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V v = get(key);
        if (v == null) {
            v = put(key, value);
        }
        return v;
    }

    public long getDefaultExpiryTime() {
        return defaultExpiryTime;
    }

    public void setDefaultExpiryTime(long defaultExpiryTime) {
        this.defaultExpiryTime = defaultExpiryTime;
    }

    @Override
    public String toString() {
        synchronized (map) {
            Iterator<Entry<K, Node<K, V>>> i = map.entrySet().iterator();
            if (!i.hasNext()) {
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

    public void onExpiry(Node<K, V> node) {

    }

    public void onEviction(Node<K, V> node) {

    }

    public void onRemove(Node<K, V> node) {

    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    public static class Node<KEY, VALUE> {
        private final ExpiryLRUMap<KEY, VALUE> expiryLRUMap;
        private final long createTimestamp = System.currentTimeMillis();
        private final long expiryTimestamp;
        private final long expiryTime;
        private final VALUE data;
        private final KEY key;
        /**
         * 是否被put方法覆盖
         */
        private volatile boolean covered = false;

        Node(long timeout, KEY key, VALUE value, ExpiryLRUMap<KEY, VALUE> expiryLRUMap) {
            long expiryTimestamp;
            this.expiryTime = timeout;
            if (timeout == Long.MAX_VALUE || timeout < 0) {
                expiryTimestamp = Long.MAX_VALUE;
            } else {
                expiryTimestamp = System.currentTimeMillis() + timeout;
                //如果算数溢出
                if (expiryTimestamp < 0) {
                    expiryTimestamp = Long.MAX_VALUE;
                }
            }
            this.expiryTimestamp = expiryTimestamp;
            this.key = key;
            this.data = value;
            this.expiryLRUMap = expiryLRUMap;
            if (expiryLRUMap != null && expiryTimestamp != Long.MAX_VALUE) {
                NO_EXPIRY_NODES.add(this);
            }
        }

        public boolean isCovered() {
            return covered;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        public ExpiryLRUMap<KEY, VALUE> getExpiryLRUMap() {
            return expiryLRUMap;
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public VALUE getDataIfExpiry() {
            if (isExpiry()) {
                return null;
            } else {
                return getData();
            }
        }

        public VALUE getData() {
            if (expiryLRUMap.isReplaceNullValueFlag()) {
                return this.data;
            } else {
                return this.data == NULL ? null : this.data;
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

        public boolean isExpiry() {
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
            while (true) {
                ExpiryLRUMap.Node node;
                try {
                    node = EXPIRY_NOTIFY_QUEUE.take();
                } catch (InterruptedException e) {
                    return;
                }
                Consumer<Node> consumer = node.expiryLRUMap.onExpiryConsumer;
                if (consumer != null) {
                    try {
                        consumer.accept(node);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static class ExpiresScan implements Runnable {
        public static final ExpiresNotify NOTIFY_INSTANCE = new ExpiresNotify();
        static final ScheduledExecutorService SCHEDULED = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("ExpiryLRUMap-ExpiresScan-" + thread.getId());
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        private static final ExpiresScan INSTANCE = new ExpiresScan();

        static {
            NOTIFY_INSTANCE.setDaemon(true);
            NOTIFY_INSTANCE.setName("ExpiryLRUMap-ExpiresNotify-" + NOTIFY_INSTANCE.getId());
            NOTIFY_INSTANCE.start();
        }

        private final Consumer<Node<?, ?>> removeConsumer = EXPIRY_NOTIFY_QUEUE::offer;

        static long getScheduleInterval() {
            String intervalMillisecond = System.getProperty("ExpiryLRUMap-ExpiresScan.interval");
            long intervalLong = 100;
            if (intervalMillisecond != null && !intervalMillisecond.isEmpty()) {
                try {
                    intervalLong = Long.parseLong(intervalMillisecond);
                } catch (Exception e) {
                    //skip
                }
            }
            return intervalLong;
        }

        public static ScheduledFuture<?> scheduleWithFixedDelay() {
            long intervalLong = getScheduleInterval();
            return SCHEDULED.scheduleWithFixedDelay(INSTANCE, intervalLong, intervalLong, TimeUnit.MICROSECONDS);
        }

        @Override
        public void run() {
            if (INSTANCE_SET.isEmpty()) {
                synchronized (INSTANCE_SET) {
                    if (INSTANCE_SET.isEmpty()) {
                        NO_EXPIRY_NODES.clear();
                        ScheduledFuture<?> scheduledFuture = SCHEDULED_FUTURE;
                        scheduledFuture.cancel(false);
                        SCHEDULED_FUTURE = null;
                    }
                }
                return;
            }
            if (NO_EXPIRY_NODES.isEmpty()) {
                return;
            }
            Node now = new Node<>(0, null, null, null);
            NavigableSet<Node> expiryNodes = NO_EXPIRY_NODES.headSet(now);
            if (expiryNodes.isEmpty()) {
                return;
            }
            Iterator<Node> iterator = expiryNodes.iterator();
            while (iterator.hasNext()) {
                Node expiryRemoveNode = iterator.next();
                synchronized (expiryRemoveNode) {
                    boolean remove = expiryRemoveNode.getExpiryLRUMap().map.remove(expiryRemoveNode.getKey(), expiryRemoveNode);
                    if (!remove && !expiryRemoveNode.covered) {
                        continue;
                    }
                }

                try {
                    iterator.remove();
                    removeConsumer.accept(expiryRemoveNode);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class Values extends AbstractCollection<V> {
        private final Collection<Node<K, V>> values;

        Values(Collection<Node<K, V>> values) {
            this.values = values;
        }

        @Override
        public Iterator<V> iterator() {
            return new Iterator<V>() {
                private Iterator<Node<K, V>> iterator = values.iterator();

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

    class EntrySet extends AbstractSet<Entry<K, V>> {
        private final Set<Entry<K, Node<K, V>>> entries;

        EntrySet(Set<Entry<K, Node<K, V>>> entries) {
            this.entries = entries;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new Iterator<Entry<K, V>>() {
                private Iterator<Entry<K, Node<K, V>>> iterator = entries.iterator();

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Entry<K, V> next() {
                    Entry<K, Node<K, V>> next = iterator.next();
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
                            Node<K, V> old = next.getValue();
                            if (old == null) {
                                return null;
                            } else {
                                next.setValue(new Node<>(old.expiryTime, key, value, ExpiryLRUMap.this));
                                return old.getData();
                            }
                        }

                        @Override
                        public boolean equals(Object o) {
                            if (this == o) {
                                return true;
                            }
                            if (!(o instanceof Map.Entry)) {
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

}
