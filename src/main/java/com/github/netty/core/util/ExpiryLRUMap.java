package com.github.netty.core.util;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Timed expiration Map will automatically expire and delete
 *  localCache
 * @author wangzihao
 */
public class ExpiryLRUMap<K, V> extends AbstractMap<K, V> {
    public static final Object NULL = new Object();
    private final Map<K, Node<V>> map;
    private boolean replaceNullValueFlag = false;
    /**
     * Default expiration time 2 minutes
     */
    private long defaultExpiryTime;
    private Collection<V> values;
    private EntrySet entrySet;
    private Function<Entry<K,Node<V>>,Boolean> removeEldestEntryFunction = this::removeEldestEntry;
    private final transient LongAdder missCount = new LongAdder();
    private final transient LongAdder hitCount = new LongAdder();

    public ExpiryLRUMap(){
        this(16,0.75F,true,1000*60*2);
    }

    public ExpiryLRUMap(long defaultExpiryTime){
        this(16,0.75F,true,defaultExpiryTime);
    }

    public ExpiryLRUMap(int initialCapacity, float loadFactor, boolean accessOrder, long defaultExpiryTime){
        this.defaultExpiryTime = defaultExpiryTime < 0 ? -1 : defaultExpiryTime;
        this.map = new LinkedHashMap<K, Node<V>>(initialCapacity,loadFactor,accessOrder){
            @Override
            protected boolean removeEldestEntry(Map.Entry<K,Node<V>> eldest) {
                if(removeEldestEntryFunction != null){
                    return removeEldestEntryFunction.apply(eldest);
                }else {
                    return false;
                }
            }
        };
    }

    public void setRemoveEldestEntryFunction(Function<Entry<K, Node<V>>, Boolean> removeEldestEntryFunction) {
        this.removeEldestEntryFunction = removeEldestEntryFunction;
    }


    public void setReplaceNullValueFlag(boolean replaceNullValueFlag) {
        this.replaceNullValueFlag = replaceNullValueFlag;
    }

    public boolean isReplaceNullValueFlag() {
        return replaceNullValueFlag;
    }

    protected boolean removeEldestEntry(Entry<K, Node<V>> eldest) {
        return false;
    }

    @Override
    public V put(K key, V value) {
        return put(key,value,defaultExpiryTime);
    }

    /**
     * @param key key
     * @param value value
     * @param expiryTime Key-value pair validity period milliseconds (Long. MAX VALUE means never expire)
     * @return old value
     */
    public V put(K key, V value, long expiryTime) {
        if(replaceNullValueFlag && value == null){
            value = (V) NULL;
        }
        Node<V> old = map.put(key, new Node<>(expiryTime, value));
        if(old == null){
            return null;
        }
        return old.getData();
    }

    @Override
    public boolean containsKey(Object key) {
        removeIfExpiry();
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
        removeIfExpiry();
        return map.containsValue(value);
    }

    @Override
    public V remove(Object key) {
        Node<V> old = map.remove(key);
        if(old == null){
            return null;
        }else {
            return old.getData();
        }
    }

    public long getMissCount() {
        return missCount.sum();
    }

    public long getHitCount() {
        return hitCount.sum();
    }

    public V atomicGet(K key, Supplier<V> supplier) {
        synchronized (map){
            Node<V> old = map.get(key);
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
        removeIfExpiry();
        Node<V> old = map.get(key);
        if(old == null){
            missCount.increment();
            return null;
        }else {
            hitCount.increment();
            return old.getData();
        }
    }

	public Node<V> getNode(K key) {
    	return map.get(key);
	}

	public Node<V> removeNode(K key) {
		return map.remove(key);
	}

	public Node<V> putNode(K key,Node<V> value) {
		return map.put(key,value);
	}

	@Override
    public Set<K> keySet() {
        removeIfExpiry();
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        removeIfExpiry();
        Collection<V> vs = values;
        if (vs == null) {
            vs = values = new Values(map.values());
        }
        return vs;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        removeIfExpiry();
        Set<Entry<K,V>> es = entrySet;
        if(entrySet == null){
            es = entrySet = new EntrySet(map.entrySet());
        }
        return es;
    }

    class Values extends AbstractCollection<V>{
        private Collection<Node<V>> values;
        Values(Collection<Node<V>> values) {
            this.values = values;
        }
        @Override
        public Iterator<V> iterator() {
            return new Iterator<V>() {
                private Iterator<Node<V>> iterator = values.iterator();
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public V next() {
                    return iterator.next().getData();
                }
            };
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    class EntrySet extends AbstractSet<Entry<K,V>>{
        private Set<Entry<K, Node<V>>> entries;
        EntrySet(Set<Entry<K, Node<V>>> entries) {
            this.entries = entries;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new Iterator<Entry<K, V>>() {
                private Iterator<Entry<K, Node<V>>> iterator = entries.iterator();
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Entry<K, V> next() {
                    Entry<K, Node<V>> next = iterator.next();
                    return new Entry<K, V>() {

                        @Override
                        public K getKey() {
                            return next.getKey();
                        }

                        @Override
                        public V getValue() {
                            return next.getValue().getData();
                        }

                        @Override
                        public V setValue(V value) {
                            Node<V> node = next.setValue(new Node<>(defaultExpiryTime, value));
                            if(node == null){
                                return null;
                            }else {
                                return node.getData();
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
            };
        }

        @Override
        public int size() {
            return entries.size();
        }
    }

    public boolean removeIfExpiry(){
        return map.entrySet().removeIf(next -> next.getValue().isExpiry());
    }

    public long getDefaultExpiryTime() {
        return defaultExpiryTime;
    }

    public void setDefaultExpiryTime(long defaultExpiryTime) {
        this.defaultExpiryTime = defaultExpiryTime;
    }

    @Override
    public String toString() {
        Iterator<Entry<K,Node<V>>> i = map.entrySet().iterator();
        if (! i.hasNext()) {
            return "{}";
        }

        long currentTime = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Entry<K,Node<V>> e = i.next();
            K key = e.getKey();
            Node<V> node = e.getValue();
            V value = node.getData();
            long timeout = node.timeout;
            sb.append(key   == this ? "(this Map)" : key);
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value);

            sb.append('|');
            sb.append((timeout - currentTime)/1000);
            sb.append("/s");

            if (! i.hasNext()) {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }

    public static void main(String[] args) {
        Map expiryMap = new ExpiryLRUMap(9999* 10000){
            @Override
            protected boolean removeEldestEntry(Entry eldest) {
                return size() > 100;
            }
        };
        for(int i=0; i< 100; i++) {
            expiryMap.put(i, i);
        }
        Object o = expiryMap.get(20);
        expiryMap.get(20);

        System.out.println("o = " + expiryMap);
    }

    public static class Node<V> {
        private long timeout;
        private V data;

        Node(long expiryTime, V value) {
            if(expiryTime == Long.MAX_VALUE){
                timeout = Long.MAX_VALUE;
            }else {
                timeout = System.currentTimeMillis() + expiryTime;
                //如果算数溢出
                if(timeout < 0){
                    timeout = Long.MAX_VALUE;
                }
            }
            this.data = value;
        }

        public V getData() {
            if(isExpiry()) {
                data = null;
            }
            return data;
        }

        public void setData(V data) {
            this.data = data;
        }

        public boolean isExpiry(){
            if(timeout == Long.MAX_VALUE){
                return false;
            }
            long currentTime = System.currentTimeMillis();
            return currentTime > timeout;
        }

        @Override
        public String toString() {
            return data == null ? "null" : data.toString();
        }
    }
}
