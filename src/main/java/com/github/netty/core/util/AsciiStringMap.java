package com.github.netty.core.util;


import io.netty.util.AsciiString;
import io.netty.util.internal.PlatformDependent;

import java.util.*;
import java.util.function.BiConsumer;

/**
 *  AsciiStringMap (note : thread unsafe!)
 *
 * @author wangzihao
 */
public class AsciiStringMap implements Map<AsciiString,AsciiString>{
    public static final AsciiStringMap EMPTY = new AsciiStringMap(0);

    private final AsciiString[] tables;
    private Collection<AsciiString> values;
    private Set<AsciiString> keySet;
    private EntrySet entrySet;
    private int maxSize;
    private int size;

    public AsciiStringMap(int maxSize) {
        this.maxSize = maxSize;
        this.tables = new AsciiString[maxSize * 2];
    }

    public AsciiString get(byte[] key){
        for(int i=0; i<tables.length; i+=2){
            AsciiString eachKey = tables[i];
            if(arrayEquals(key,eachKey)){
                return tables[i + 1];
            }
        }
        return null;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size > 0;
    }

    @Override
    public boolean containsKey(Object key) {
        for(int i=0; i<tables.length; i+=2){
            if(tables[i].equals(key)){
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        for(int i=0; i<tables.length; i+=2){
            if(tables[i+1].equals(value)){
                return true;
            }
        }
        return false;
    }

    @Override
    public AsciiString get(Object key) {
        for(int i=0; i<tables.length; i+=2){
            if(tables[i].equals(key)){
                return tables[i+1];
            }
        }
        return null;
    }

    @Override
    public AsciiString put(AsciiString key, AsciiString value){
        if(key == null || value == null){
            throw new NullPointerException();
        }

        for(int i=0; i<tables.length; i+=2){
            AsciiString oldKey = tables[i];
            if(oldKey == null){
                continue;
            }
            if(key.equals(oldKey)){
                AsciiString oldValue = tables[i + 1];
                tables[i+1] = value;
                return oldValue;
            }
        }

        for(int i=0; i<tables.length; i+=2){
            AsciiString oldKey = tables[i];
            AsciiString oldValue = tables[i+1];
            if(oldKey == null){
                tables[i] = key;
                tables[i+1] = value;
                size++;
                return oldValue;
            }
        }

        throw new IndexOutOfBoundsException("OutOf dataCount. maxDataCount="+ maxSize);
    }

    @Override
    public AsciiString remove(Object key) {
        for(int i=0; i<tables.length; i+=2){
            AsciiString eachKey = tables[i];
            if(eachKey == null){
                continue;
            }
            if(eachKey.equals(key)){
                AsciiString oldValue = tables[i+1];
                tables[i] = null;
                tables[i+1] = null;
                size--;
                return oldValue;
            }
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends AsciiString, ? extends AsciiString> m) {
        for (Map.Entry<? extends AsciiString, ? extends AsciiString> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        for(int i=0; i<tables.length; i++){
            tables[i] = null;
        }
        size = 0;
    }

    @Override
    public Set<AsciiString> keySet() {
        Set<AsciiString> ks = keySet;
        if (ks == null) {
            ks = keySet = new KeySet();
        }
        return ks;
    }

    @Override
    public Collection<AsciiString> values() {
        Collection<AsciiString> vs = values;
        if (vs == null) {
            vs = values = new Values();
        }
        return vs;
    }

    @Override
    public Set<Entry<AsciiString, AsciiString>> entrySet() {
        Set<Map.Entry<AsciiString,AsciiString>> es = entrySet;
        if(entrySet == null){
            es = entrySet = new EntrySet();
        }
        return es;
    }


    class KeySet extends AbstractSet<AsciiString>{
        @Override
        public Iterator<AsciiString> iterator() {
            return new Iterator<AsciiString>() {
                private int index;
                @Override
                public boolean hasNext() {
                    for(int i= index; i< tables.length; i++){
                        if(tables[i] != null){
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public AsciiString next() {
                    AsciiString value = tables[index];
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

    class Values extends AbstractCollection<AsciiString>{
        @Override
        public Iterator<AsciiString> iterator() {
            return new Iterator<AsciiString>() {
                private int index;
                @Override
                public boolean hasNext() {
                    for(int i= index; i< tables.length; i++){
                        if(tables[i] != null){
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public AsciiString next() {
                    AsciiString value = tables[index + 1];
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

    class EntrySet extends AbstractSet<Map.Entry<AsciiString,AsciiString>>{
        @Override
        public Iterator<Entry<AsciiString, AsciiString>> iterator() {
            return new Iterator<Entry<AsciiString, AsciiString>>() {
                private int index;
                @Override
                public boolean hasNext() {
                    for(int i= index; i< tables.length; i++){
                        if(tables[i] != null){
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public Entry<AsciiString, AsciiString> next() {
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

    class Node implements Map.Entry<AsciiString,AsciiString>{
        private int index;
        Node(int index) {
            this.index = index;
        }

        @Override
        public AsciiString getKey() {
            return tables[index];
        }

        @Override
        public AsciiString getValue() {
            return tables[index+1];
        }

        @Override
        public AsciiString setValue(AsciiString value) {
            AsciiString oldValue = tables[index+1];
            tables[index+1] = value;
            return oldValue;
        }

        @Override
        public String toString() {
            return getKey() + "=" + getValue();
        }
    }

    public void each(BiConsumer<AsciiString,AsciiString> eachConsumer){
        for(int i=0; i<tables.length; i+=2){
            eachConsumer.accept(tables[i], tables[i + 1]);
        }
    }

    public int getMaxSize() {
        return maxSize;
    }

    private boolean arrayEquals(byte[] value1, AsciiString value2) {
        return value1.length == value2.length() &&
                PlatformDependent.equals(value1, 0, value2.array(), value2.arrayOffset(), value1.length);
    }

    @Override
    public String toString() {
        Iterator<Entry<AsciiString,AsciiString>> i = entrySet().iterator();
        if (! i.hasNext()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Entry<AsciiString,AsciiString> e = i.next();
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