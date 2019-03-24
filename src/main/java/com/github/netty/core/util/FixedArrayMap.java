package com.github.netty.core.util;

import java.io.Serializable;
import java.util.*;

/**
 *  FixedArrayMap (note : thread unsafe!)
 *
 * @author wangzihao
 */
public class FixedArrayMap<T> implements Map<T,T>, Serializable {
    private static final long serialVersionUID = -1L;

    private final T[] tables;
    private Collection<T> values;
    private Set<T> keySet;
    private EntrySet entrySet;
    private int maxSize;
    private int size;
    private transient int modCount;

    /**
     * @param maxSize Can store up to maxSize key-value pairs
     */
    public FixedArrayMap(int maxSize) {
        this.maxSize = maxSize;
        this.tables = (T[]) new Object[maxSize * 2];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        for(int i=0; i<tables.length; i+=2){
            if(tables[i] != null && tables[i].equals(key)){
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        for(int i=0; i<tables.length; i+=2){
            if(tables[+1] != null && tables[i+1].equals(value)){
                return true;
            }
        }
        return false;
    }

    @Override
    public T get(Object key) {
        int comparisonCount = 0;
        for(int i=0; i<tables.length; i+=2){
            if(tables[i] == null) {
                continue;
            }
            comparisonCount++;
            if(tables[i].equals(key)) {
                return tables[i + 1];
            }else if(comparisonCount >= size){
                return null;
            }
        }
        return null;
    }

    @Override
    public T put(T key, T value){
        if(key == null || value == null){
            throw new NullPointerException();
        }

        for(int i=0; i<tables.length; i+=2){
            T oldKey = tables[i];
            if(oldKey == null){
                continue;
            }
            if(key.equals(oldKey)){
                T oldValue = tables[i + 1];
                tables[i+1] = value;
                modCount++;
                return oldValue;
            }
        }

        for(int i=0; i<tables.length; i+=2){
            T oldKey = tables[i];
            T oldValue = tables[i+1];
            if(oldKey == null){
                tables[i] = key;
                tables[i+1] = value;
                size++;
                modCount++;
                return oldValue;
            }
        }

        throw new IndexOutOfBoundsException("OutOf element maxSize. maxSize="+ maxSize);
    }

    @Override
    public T remove(Object key) {
        if(size == 0){
            return null;
        }
        for(int i=0; i<tables.length; i+=2){
            T eachKey = tables[i];
            if(eachKey == null){
                continue;
            }
            if(eachKey.equals(key)){
                T oldValue = tables[i+1];
                tables[i] = null;
                tables[i+1] = null;
                size--;
                modCount++;
                return oldValue;
            }
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends T, ? extends T> m) {
        for (Map.Entry<? extends T, ? extends T> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        for(int i=0; i<tables.length; i++){
            tables[i] = null;
        }
        modCount++;
        size = 0;
    }

    @Override
    public Set<T> keySet() {
        Set<T> ks = keySet;
        if (ks == null) {
            ks = keySet = new KeySet();
        }
        return ks;
    }

    @Override
    public Collection<T> values() {
        Collection<T> vs = values;
        if (vs == null) {
            vs = values = new Values();
        }
        return vs;
    }

    @Override
    public Set<Entry<T, T>> entrySet() {
        Set<Map.Entry<T,T>> es = entrySet;
        if(entrySet == null){
            es = entrySet = new EntrySet();
        }
        return es;
    }


    class KeySet extends AbstractSet<T>{
        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                private int index;
                private int comparisonCount;
                private int expectedModCount = modCount;
                @Override
                public boolean hasNext() {
                    for(int i= index; i< tables.length; i++){
                        if(tables[i] != null){
                            comparisonCount++;
                            return true;
                        }
                        if(comparisonCount >= size){
                            return false;
                        }
                    }
                    return false;
                }

                @Override
                public T next() {
                    if (modCount != expectedModCount) {
                        throw new ConcurrentModificationException();
                    }
                    T value = tables[index];
                    index +=2;
                    return value;
                }
            };
        }

        @Override
        public int size() {
            return size;
        }
    }

    class Values extends AbstractCollection<T>{
        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                private int index;
                private int comparisonCount;
                private int expectedModCount = modCount;
                @Override
                public boolean hasNext() {
                    for(int i= index; i< tables.length; i++){
                        if(tables[i] != null){
                            comparisonCount++;
                            return true;
                        }
                        if(comparisonCount >= size){
                            return false;
                        }
                    }
                    return false;
                }

                @Override
                public T next() {
                    if (modCount != expectedModCount) {
                        throw new ConcurrentModificationException();
                    }
                    T value = tables[index + 1];
                    index +=2;
                    return value;
                }
            };
        }

        @Override
        public int size() {
            return size;
        }
    }

    class EntrySet extends AbstractSet<Map.Entry<T,T>>{
        @Override
        public Iterator<Entry<T, T>> iterator() {
            return new Iterator<Entry<T, T>>() {
                private int index;
                private int comparisonCount;
                private int expectedModCount = modCount;
                @Override
                public boolean hasNext() {
                    for(int i= index; i< tables.length; i++){
                        if(tables[i] != null){
                            comparisonCount++;
                            return true;
                        }
                        if(comparisonCount >= size){
                            return false;
                        }
                    }
                    return false;
                }

                @Override
                public Entry<T, T> next() {
                    if (modCount != expectedModCount) {
                        throw new ConcurrentModificationException();
                    }
                    Node node = new Node(index);
                    index +=2;
                    return node;
                }
            };
        }

        @Override
        public int size() {
            return size;
        }
    }

    class Node implements Map.Entry<T,T>{
        int index;

        public Node(int index) {
            this.index = index;
        }

        @Override
        public T getKey() {
            return tables[index];
        }

        @Override
        public T getValue() {
            return tables[index+1];
        }

        @Override
        public T setValue(T value) {
            T oldValue = tables[index+1];
            tables[index+1] = value;
            return oldValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Node node = (Node) o;
            return Objects.equals(getKey(), node.getKey()) &&
                    Objects.equals(getValue(), node.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getKey(), getValue());
        }

        @Override
        public String toString() {
            return getKey() + "=" + getValue();
        }
    }

    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public String toString() {
        Iterator<Entry<T,T>> i = entrySet().iterator();
        if (! i.hasNext()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Entry<T,T> e = i.next();
            sb.append(e.getKey());
            sb.append('=');
            sb.append(e.getValue());
            if (! i.hasNext()) {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }
}