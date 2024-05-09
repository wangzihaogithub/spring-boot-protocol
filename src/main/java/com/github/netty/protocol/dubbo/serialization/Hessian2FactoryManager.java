/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.netty.protocol.dubbo.serialization;

import com.alibaba.com.caucho.hessian.io.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class Hessian2FactoryManager {
    private final ConcurrentHashMap<ClassLoader, SerializerFactory> CL_2_SERIALIZER_FACTORY = new ConcurrentHashMap<>();
    private final SerializeSecurityManager serializeSecurityManager;
    private final DefaultSerializeClassChecker defaultSerializeClassChecker;
    String WHITELIST = "dubbo.application.hessian2.whitelist";
    String ALLOW = "dubbo.application.hessian2.allow";
    String DENY = "dubbo.application.hessian2.deny";
    private volatile SerializerFactory SYSTEM_SERIALIZER_FACTORY;
    private volatile SerializerFactory stickySerializerFactory = null;

    public Hessian2FactoryManager() {
        serializeSecurityManager = SerializeSecurityManager.INSTANCE;
        defaultSerializeClassChecker = DefaultSerializeClassChecker.INSTANCE;
    }

    public static <K, V> V computeIfAbsent(ConcurrentMap<K, V> map, K key, Function<? super K, ? extends V> func) {
        V v = map.get(key);
        if (null == v) {
            // issue#11986 lock bug
            // v = map.computeIfAbsent(key, func);

            // this bug fix methods maybe cause `func.apply` multiple calls.
            v = func.apply(key);
            if (null == v) {
                return null;
            }
            final V res = map.putIfAbsent(key, v);
            if (null != res) {
                // if pre value present, means other thread put value already, and putIfAbsent not effect
                // return exist value
                return res;
            }
            // if pre value is null, means putIfAbsent effected, return current value
        }
        return v;
    }

    public SerializerFactory getSerializerFactory(ClassLoader classLoader) {
        SerializerFactory sticky = stickySerializerFactory;
        if (sticky != null && Objects.equals(sticky.getClassLoader(), classLoader)) {
            return sticky;
        }

        if (classLoader == null) {
            // system classloader
            if (SYSTEM_SERIALIZER_FACTORY == null) {
                synchronized (this) {
                    if (SYSTEM_SERIALIZER_FACTORY == null) {
                        SYSTEM_SERIALIZER_FACTORY = createSerializerFactory(null);
                    }
                }
            }
            stickySerializerFactory = SYSTEM_SERIALIZER_FACTORY;
            return SYSTEM_SERIALIZER_FACTORY;
        }

        SerializerFactory factory = computeIfAbsent(CL_2_SERIALIZER_FACTORY, classLoader,
                this::createSerializerFactory);
        stickySerializerFactory = factory;
        return factory;
    }

    private SerializerFactory createSerializerFactory(ClassLoader classLoader) {
        String whitelist = System.getProperty(WHITELIST);
        if (whitelist != null && !whitelist.isEmpty()) {
            return createWhiteListSerializerFactory(classLoader);
        }
        return createDefaultSerializerFactory(classLoader);
    }

    private SerializerFactory createDefaultSerializerFactory(ClassLoader classLoader) {
        Hessian2SerializerFactory hessian2SerializerFactory = new Hessian2SerializerFactory(classLoader,
                defaultSerializeClassChecker);
        hessian2SerializerFactory.setAllowNonSerializable(Boolean.parseBoolean(System.getProperty("dubbo.hessian"
                + ".allowNonSerializable", "false")));
        hessian2SerializerFactory.getClassFactory()
                .allow("org.apache.dubbo.*");
        return hessian2SerializerFactory;
    }

    public SerializerFactory createWhiteListSerializerFactory(ClassLoader classLoader) {
        SerializerFactory serializerFactory = new Hessian2SerializerFactory(classLoader, defaultSerializeClassChecker);
        String whiteList = System.getProperty(WHITELIST);
        if ("true".equals(whiteList)) {
            serializerFactory.getClassFactory()
                    .setWhitelist(true);
            String allowPattern = System.getProperty(ALLOW);
            if (allowPattern != null && !allowPattern.isEmpty()) {
                for (String pattern : allowPattern.split(";")) {
                    serializerFactory.getClassFactory()
                            .allow(pattern);
                    serializeSecurityManager.addToAlwaysAllowed(pattern);
                }
            }
            serializeSecurityManager.setCheckStatus(SerializeCheckStatus.STRICT);
        } else {
            serializerFactory.getClassFactory()
                    .setWhitelist(false);
            String denyPattern = System.getProperty(DENY);
            if (denyPattern != null && !denyPattern.isEmpty()) {
                for (String pattern : denyPattern.split(";")) {
                    serializerFactory.getClassFactory()
                            .deny(pattern);
                    serializeSecurityManager.addToDisAllowed(pattern);
                }
            }
        }
        serializerFactory.setAllowNonSerializable(Boolean.parseBoolean(System.getProperty("dubbo.hessian"
                + ".allowNonSerializable", "false")));
        serializerFactory.getClassFactory()
                .allow("org.apache.dubbo.*");
        return serializerFactory;
    }

    public void onRemoveClassLoader(ClassLoader classLoader) {
        CL_2_SERIALIZER_FACTORY.remove(classLoader);
    }

    public static class LazyMapDeserializer extends MapDeserializer {
        private final Class type;

        public LazyMapDeserializer(Class type) {
            super(type);
            this.type = type;
        }

        @Override
        public Object readMap(AbstractHessianInput in, Class<?> expectKeyType, Class<?> expectValueType) throws IOException {
            Deserializer keyDeserializer = null, valueDeserializer = null;
            SerializerFactory factory = findSerializerFactory(in);
            if (expectKeyType != null) {
                keyDeserializer = factory.getDeserializer(expectKeyType.getName());
            }
            if (expectValueType != null) {
                valueDeserializer = factory.getDeserializer(expectValueType.getName());
            }
            LazyMap map = new LazyMap(in, keyDeserializer, valueDeserializer);
            in.addRef(map);
            in.skipOptionalCall();
            return map;
        }

        public static class LazyMap<K, V> extends HashMap<K, V> {
            private final AbstractHessianInput in;
            private final Deserializer keyDeserializer, valueDeserializer;

            public LazyMap(AbstractHessianInput in, Deserializer keyDeserializer, Deserializer valueDeserializer) {
                this.in = in;
                this.keyDeserializer = keyDeserializer;
                this.valueDeserializer = valueDeserializer;
            }

            public void readAll() {
                while (true) {
                    try {
                        if (!next()) {
                            break;
                        }
                    } catch (IOException ignored) {
                        break;
                    }
                }
            }

            @Override
            public boolean isEmpty() {
                boolean empty = super.isEmpty();
                if (empty) {
                    try {
                        next();
                    } catch (IOException ignored) {

                    }
                }
                return super.isEmpty();
            }

            @Override
            public V get(Object key) {
                while (true) {
                    if (super.containsKey(key)) {
                        return super.get(key);
                    }
                    try {
                        if (!next()) {
                            return null;
                        }
                    } catch (IOException e) {
                        return null;
                    }
                }
            }

            @Override
            public V getOrDefault(Object key, V defaultValue) {
                while (true) {
                    if (super.containsKey(key)) {
                        return super.get(key);
                    }
                    try {
                        if (!next()) {
                            return defaultValue;
                        }
                    } catch (IOException e) {
                        return null;
                    }
                }
            }

            private boolean next() throws IOException {
                if (in == null || in.isEnd()) {
                    return false;
                } else {
                    Object key = keyDeserializer != null ? keyDeserializer.readObject(in) : in.readObject();
                    Object value = valueDeserializer != null ? valueDeserializer.readObject(in) : in.readObject();
                    if (in.isEnd()) {
                        in.readEnd();
                    }
                    put((K) key, (V) value);
                    return true;
                }
            }
        }
    }

    public static class Hessian2SerializerFactory extends SerializerFactory {
        private final DefaultSerializeClassChecker defaultSerializeClassChecker;
        private final Map<Class, LazyMapDeserializer> mapDeserializerCache = new ConcurrentHashMap<>(3);

        public Hessian2SerializerFactory(
                ClassLoader classLoader, DefaultSerializeClassChecker defaultSerializeClassChecker) {
            super(classLoader);
            this.defaultSerializeClassChecker = defaultSerializeClassChecker;
        }

        @Override
        public Deserializer getDeserializer(Class cl) throws HessianProtocolException {
            if (LazyMapDeserializer.LazyMap.class == cl) {
                return mapDeserializerCache.computeIfAbsent(cl, LazyMapDeserializer::new);
            } else {
                return super.getDeserializer(cl);
            }
        }

        @Override
        public Class<?> loadSerializedClass(String className) throws ClassNotFoundException {
            return defaultSerializeClassChecker.loadClass(getClassLoader(), className);
        }

        @Override
        protected com.alibaba.com.caucho.hessian.io.Serializer getDefaultSerializer(Class cl) {
            if (_defaultSerializer != null) {
                return _defaultSerializer;
            }

            try {
                // pre-check if class is allow
                defaultSerializeClassChecker.loadClass(getClassLoader(), cl.getName());
            } catch (ClassNotFoundException e) {
                // ignore
            }

            checkSerializable(cl);

            return new JavaSerializer(cl, getClassLoader());
        }

        @Override
        protected Deserializer getDefaultDeserializer(Class cl) {
            try {
                // pre-check if class is allow
                defaultSerializeClassChecker.loadClass(getClassLoader(), cl.getName());
            } catch (ClassNotFoundException e) {
                // ignore
            }

            checkSerializable(cl);

            return new JavaDeserializer(cl);
        }

        private void checkSerializable(Class<?> cl) {
            // If class is Serializable => ok
            // If class has not implement Serializable
            //      If hessian check serializable => fail
            //      If dubbo class checker check serializable => fail
            //      If both hessian and dubbo class checker allow non-serializable => ok
            if (!Serializable.class.isAssignableFrom(cl) && (!isAllowNonSerializable()
                    || defaultSerializeClassChecker.isCheckSerializable())) {
                throw new IllegalStateException(
                        "Serialized class " + cl.getName() + " must implement java.io.Serializable");
            }
        }
    }

}
