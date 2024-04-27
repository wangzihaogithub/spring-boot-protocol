package com.github.netty.core.util;

import java.io.Serializable;
import java.util.*;

/**
 * <p>This Map implementation is generally not thread-safe. It is primarily designed
 * for data structures exposed from request objects, for use in a single thread only.
 *
 * @param <K> the key type
 * @param <V> the value element type
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 3.0
 */
public class LinkedMultiValueMap<K, V> implements Map<K, List<V>>, Serializable, Cloneable {

    private static final long serialVersionUID = 3801124242820219131L;

    private final Map<K, List<V>> targetMap;


    /**
     * Create a new LinkedMultiValueMap that wraps a {@link LinkedHashMap}.
     */
    public LinkedMultiValueMap() {
        this.targetMap = new LinkedHashMap<>();
    }

    /**
     * Create a new LinkedMultiValueMap that wraps a {@link LinkedHashMap}
     * with the given initial capacity.
     *
     * @param initialCapacity the initial capacity
     */
    public LinkedMultiValueMap(int initialCapacity) {
        this.targetMap = new LinkedHashMap<>(initialCapacity);
    }


    public LinkedMultiValueMap(Map<K, List<V>> targetMap) {
        this.targetMap = Objects.requireNonNull(targetMap);
    }


    public V getFirst(K key) {
        List<V> values = this.targetMap.get(key);
        return (values != null ? values.get(0) : null);
    }


    public void add(K key, V value) {
        List<V> values = this.targetMap.computeIfAbsent(key, k -> new ArrayList<>(2));
        values.add(value);
    }


    public void addAll(K key, List<? extends V> values) {
        List<V> currentValues = this.targetMap.computeIfAbsent(key, k -> new ArrayList<>(values.size()));
        currentValues.addAll(values);
    }

    public void set(K key, V value) {
        List<V> values = new ArrayList<>(1);
        values.add(value);
        this.targetMap.put(key, values);
    }


    public void setAll(Map<K, V> values) {
        values.forEach(this::set);
    }


    public Map<K, V> toSingleValueMap() {
        LinkedHashMap<K, V> singleValueMap = new LinkedHashMap<>(this.targetMap.size());
        this.targetMap.forEach((key, value) -> singleValueMap.put(key, value.get(0)));
        return singleValueMap;
    }


    // Map implementation

    @Override
    public int size() {
        return this.targetMap.size();
    }

    @Override
    public boolean isEmpty() {
        return this.targetMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.targetMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return this.targetMap.containsValue(value);
    }

    @Override
    public List<V> get(Object key) {
        return this.targetMap.get(key);
    }

    @Override
    public List<V> put(K key, List<V> value) {
        return this.targetMap.put(key, value);
    }

    @Override
    public List<V> remove(Object key) {
        return this.targetMap.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends List<V>> map) {
        this.targetMap.putAll(map);
    }

    @Override
    public void clear() {
        this.targetMap.clear();
    }

    @Override
    public Set<K> keySet() {
        return this.targetMap.keySet();
    }

    @Override
    public Collection<List<V>> values() {
        return this.targetMap.values();
    }

    @Override
    public Set<Map.Entry<K, List<V>>> entrySet() {
        return this.targetMap.entrySet();
    }

    @Override
    public boolean equals(Object obj) {
        return this.targetMap.equals(obj);
    }


    @Override
    public int hashCode() {
        return this.targetMap.hashCode();
    }

    @Override
    public String toString() {
        return this.targetMap.toString();
    }

}
