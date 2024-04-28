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
package com.github.netty.javadubbo.example;

import com.alibaba.com.caucho.hessian.io.*;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class Hessian2FactoryManager {
    String WHITELIST = "dubbo.application.hessian2.whitelist";
    String ALLOW = "dubbo.application.hessian2.allow";
    String DENY = "dubbo.application.hessian2.deny";
    private volatile SerializerFactory SYSTEM_SERIALIZER_FACTORY;
    private volatile SerializerFactory stickySerializerFactory = null;
    private final ConcurrentHashMap<ClassLoader, SerializerFactory> CL_2_SERIALIZER_FACTORY = new ConcurrentHashMap<>();

    private final SerializeSecurityManager serializeSecurityManager;
    private final DefaultSerializeClassChecker defaultSerializeClassChecker;

    public Hessian2FactoryManager() {
        serializeSecurityManager = new SerializeSecurityManager();
        defaultSerializeClassChecker = new DefaultSerializeClassChecker(serializeSecurityManager);
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

    public enum SerializeCheckStatus {
        /**
         * Disable serialize check for all classes
         */
        DISABLE(0),

        /**
         * Only deny danger classes, warn if other classes are not in allow list
         */
        WARN(1),

        /**
         * Only allow classes in allow list, deny if other classes are not in allow list
         */
        STRICT(2);

        private final int level;

        SerializeCheckStatus(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }
    }

    public void onRemoveClassLoader(ClassLoader classLoader) {
        CL_2_SERIALIZER_FACTORY.remove(classLoader);
    }

    public static class Hessian2SerializerFactory extends SerializerFactory {

        private final DefaultSerializeClassChecker defaultSerializeClassChecker;

        public Hessian2SerializerFactory(
                ClassLoader classLoader, DefaultSerializeClassChecker defaultSerializeClassChecker) {
            super(classLoader);
            this.defaultSerializeClassChecker = defaultSerializeClassChecker;
        }

        @Override
        public Class<?> loadSerializedClass(String className) throws ClassNotFoundException {
            return defaultSerializeClassChecker.loadClass(getClassLoader(), className);
        }

        @Override
        protected com.alibaba.com.caucho.hessian.io.Serializer getDefaultSerializer(Class cl) {
            if (_defaultSerializer != null) {return _defaultSerializer;}

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

    public static class SerializeSecurityManager {
        //        private static final ErrorTypeAwareLogger logger =
        //                LoggerFactory.getErrorTypeAwareLogger(SerializeSecurityManager.class);

        private final Set<String> allowedPrefix = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private final Set<String> alwaysAllowedPrefix = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private final Set<String> disAllowedPrefix = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private final Set<DefaultSerializeClassChecker> listeners =
                Collections.newSetFromMap(new ConcurrentHashMap<>());

        private final Set<String> warnedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private volatile SerializeCheckStatus checkStatus = null;

        private volatile SerializeCheckStatus defaultCheckStatus = SerializeCheckStatus.STRICT;

        private volatile Boolean checkSerializable = null;

        public void addToAlwaysAllowed(String className) {
            boolean modified = alwaysAllowedPrefix.add(className);

            if (modified) {
                notifyPrefix();
            }
        }

        public void addToAllowed(String className) {
            if (disAllowedPrefix.stream()
                    .anyMatch(className::startsWith)) {
                return;
            }

            boolean modified = allowedPrefix.add(className);

            if (modified) {
                notifyPrefix();
            }
        }

        public void addToDisAllowed(String className) {
            boolean modified = disAllowedPrefix.add(className);
            modified = allowedPrefix.removeIf(allow -> allow.startsWith(className)) || modified;

            if (modified) {
                notifyPrefix();
            }

            String lowerCase = className.toLowerCase(Locale.ROOT);
            if (!Objects.equals(lowerCase, className)) {
                addToDisAllowed(lowerCase);
            }
        }

        public void setCheckStatus(SerializeCheckStatus checkStatus) {
            if (this.checkStatus == null) {
                this.checkStatus = checkStatus;
                //                logger.info("Serialize check level: " + checkStatus.name());
                notifyCheckStatus();
                return;
            }

            // If has been set to WARN, ignore STRICT
            if (this.checkStatus.level() <= checkStatus.level()) {
                return;
            }

            this.checkStatus = checkStatus;
            //            logger.info("Serialize check level: " + checkStatus.name());
            notifyCheckStatus();
        }

        public void setDefaultCheckStatus(SerializeCheckStatus checkStatus) {
            this.defaultCheckStatus = checkStatus;
            //            logger.info("Serialize check default level: " + checkStatus.name());
            notifyCheckStatus();
        }

        public void setCheckSerializable(boolean checkSerializable) {
            if (this.checkSerializable == null || (Boolean.TRUE.equals(this.checkSerializable) && !checkSerializable)) {
                this.checkSerializable = checkSerializable;
                //                logger.info("Serialize check serializable: " + checkSerializable);
                notifyCheckSerializable();
            }
        }

        public void registerListener(DefaultSerializeClassChecker listener) {
            listeners.add(listener);
            listener.notifyPrefix(getAllowedPrefix(), getDisAllowedPrefix());
            listener.notifyCheckSerializable(isCheckSerializable());
            listener.notifyCheckStatus(getCheckStatus());
        }

        private void notifyPrefix() {
            for (DefaultSerializeClassChecker listener : listeners) {
                listener.notifyPrefix(getAllowedPrefix(), getDisAllowedPrefix());
            }
        }

        private void notifyCheckStatus() {
            for (DefaultSerializeClassChecker listener : listeners) {
                listener.notifyCheckStatus(getCheckStatus());
            }
        }

        private void notifyCheckSerializable() {
            for (DefaultSerializeClassChecker listener : listeners) {
                listener.notifyCheckSerializable(isCheckSerializable());
            }
        }

        protected SerializeCheckStatus getCheckStatus() {
            return checkStatus == null ? defaultCheckStatus : checkStatus;
        }

        protected Set<String> getAllowedPrefix() {
            Set<String> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
            set.addAll(allowedPrefix);
            set.addAll(alwaysAllowedPrefix);
            return set;
        }

        protected Set<String> getDisAllowedPrefix() {
            Set<String> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
            set.addAll(disAllowedPrefix);
            return set;
        }

        protected boolean isCheckSerializable() {
            return checkSerializable == null || checkSerializable;
        }

        public Set<String> getWarnedClasses() {
            return warnedClasses;
        }
    }

    public static class DefaultSerializeClassChecker {

        private static final long MAGIC_HASH_CODE = 0xcbf29ce484222325L;
        private static final long MAGIC_PRIME = 0x100000001b3L;
        //        private static final ErrorTypeAwareLogger logger =
        //                LoggerFactory.getErrorTypeAwareLogger(DefaultSerializeClassChecker.class);
        private volatile SerializeCheckStatus checkStatus = SerializeCheckStatus.STRICT;
        private volatile boolean checkSerializable = true;

        private final SerializeSecurityManager serializeSecurityManager;
        private volatile long[] allowPrefixes = new long[0];

        private volatile long[] disAllowPrefixes = new long[0];

        public DefaultSerializeClassChecker(SerializeSecurityManager manager) {
            serializeSecurityManager = manager;
            serializeSecurityManager.registerListener(this);
        }

        public synchronized void notifyPrefix(Set<String> allowedList, Set<String> disAllowedList) {
            this.allowPrefixes = loadPrefix(allowedList);
            this.disAllowPrefixes = loadPrefix(disAllowedList);
        }

        public synchronized void notifyCheckStatus(SerializeCheckStatus status) {
            this.checkStatus = status;
        }

        public synchronized void notifyCheckSerializable(boolean checkSerializable) {
            this.checkSerializable = checkSerializable;
        }

        private static long[] loadPrefix(Set<String> allowedList) {
            long[] array = new long[allowedList.size()];

            int index = 0;
            for (String name : allowedList) {
                if (name == null || name.isEmpty()) {
                    continue;
                }

                long hashCode = MAGIC_HASH_CODE;
                for (int j = 0; j < name.length(); ++j) {
                    char ch = name.charAt(j);
                    if (ch == '$') {
                        ch = '.';
                    }
                    hashCode ^= ch;
                    hashCode *= MAGIC_PRIME;
                }

                array[index++] = hashCode;
            }

            if (index != array.length) {
                array = Arrays.copyOf(array, index);
            }
            Arrays.sort(array);
            return array;
        }

        /**
         * Try load class
         *
         * @param className class name
         * @throws IllegalArgumentException if class is blocked
         */
        public Class<?> loadClass(ClassLoader classLoader, String className) throws ClassNotFoundException {
            Class<?> aClass = loadClass0(classLoader, className);
            if (!aClass.isPrimitive() && !Serializable.class.isAssignableFrom(aClass)) {
                String msg = "[Serialization Security] Serialized class " + className
                        + " has not implement Serializable interface. "
                        + "Current mode is strict check, will disallow to deserialize it by default. ";
                if (serializeSecurityManager.getWarnedClasses()
                        .add(className)) {
                    //                    logger.error(PROTOCOL_UNTRUSTED_SERIALIZE_CLASS, "", "", msg);
                }

                if (checkSerializable) {
                    throw new IllegalArgumentException(msg);
                }
            }

            return aClass;
        }

        private Class<?> loadClass0(ClassLoader classLoader, String className) throws ClassNotFoundException {
            if (checkStatus == SerializeCheckStatus.DISABLE) {
                return ClassUtils.forName(className, classLoader);
            }

            long hash = MAGIC_HASH_CODE;
            for (int i = 0, typeNameLength = className.length(); i < typeNameLength; ++i) {
                char ch = className.charAt(i);
                if (ch == '$') {
                    ch = '.';
                }
                hash ^= ch;
                hash *= MAGIC_PRIME;

                if (Arrays.binarySearch(allowPrefixes, hash) >= 0) {
                    return ClassUtils.forName(className, classLoader);
                }
            }

            if (checkStatus == SerializeCheckStatus.STRICT) {
                String msg = "[Serialization Security] Serialized class " + className + " is not in allow list. "
                        + "Current mode is `STRICT`, will disallow to deserialize it by default. "
                        + "Please add it into security/serialize.allowlist or follow FAQ to configure it.";
                if (serializeSecurityManager.getWarnedClasses()
                        .add(className)) {
                    //                    logger.error(PROTOCOL_UNTRUSTED_SERIALIZE_CLASS, "", "", msg);
                }

                throw new IllegalArgumentException(msg);
            }

            hash = MAGIC_HASH_CODE;
            for (int i = 0, typeNameLength = className.length(); i < typeNameLength; ++i) {
                char ch = className.charAt(i);
                if (ch == '$') {
                    ch = '.';
                }
                hash ^= ch;
                hash *= MAGIC_PRIME;

                if (Arrays.binarySearch(disAllowPrefixes, hash) >= 0) {
                    String msg = "[Serialization Security] Serialized class " + className + " is in disallow list. "
                            + "Current mode is `WARN`, will disallow to deserialize it by default. "
                            + "Please add it into security/serialize.allowlist or follow FAQ to configure it.";
                    if (serializeSecurityManager.getWarnedClasses()
                            .add(className)) {
                        //                        logger.warn(PROTOCOL_UNTRUSTED_SERIALIZE_CLASS, "", "", msg);
                    }

                    throw new IllegalArgumentException(msg);
                }
            }

            hash = MAGIC_HASH_CODE;
            for (int i = 0, typeNameLength = className.length(); i < typeNameLength; ++i) {
                char ch = Character.toLowerCase(className.charAt(i));
                if (ch == '$') {
                    ch = '.';
                }
                hash ^= ch;
                hash *= MAGIC_PRIME;

                if (Arrays.binarySearch(disAllowPrefixes, hash) >= 0) {
                    String msg = "[Serialization Security] Serialized class " + className + " is in disallow list. "
                            + "Current mode is `WARN`, will disallow to deserialize it by default. "
                            + "Please add it into security/serialize.allowlist or follow FAQ to configure it.";
                    if (serializeSecurityManager.getWarnedClasses()
                            .add(className)) {
                        //                        logger.warn(PROTOCOL_UNTRUSTED_SERIALIZE_CLASS, "", "", msg);
                    }

                    throw new IllegalArgumentException(msg);
                }
            }

            Class<?> clazz = ClassUtils.forName(className, classLoader);
            if (serializeSecurityManager.getWarnedClasses()
                    .add(className)) {
                //                logger.warn(
                //                        PROTOCOL_UNTRUSTED_SERIALIZE_CLASS,
                //                        "",
                //                        "",
                //                        "[Serialization Security] Serialized class " + className + " is not in
                //                        allow list. "
                //                                + "Current mode is `WARN`, will allow to deserialize it by default. "
                //                                + "Dubbo will set to `STRICT` mode by default in the future. "
                //                                + "Please add it into security/serialize.allowlist or follow FAQ to
                //                                configure it.");
            }
            return clazz;
        }

        public boolean isCheckSerializable() {
            return checkSerializable;
        }
    }

    public static class ClassUtils {
        /**
         * Suffix for array class names: "[]"
         */
        public static final String ARRAY_SUFFIX = "[]";
        /**
         * Prefix for internal array class names: "[L"
         */
        private static final String INTERNAL_ARRAY_PREFIX = "[L";
        /**
         * Map with primitive type name as key and corresponding primitive type as value, for example: "int" ->
         * "int.class".
         */
        private static final Map<String, Class<?>> PRIMITIVE_TYPE_NAME_MAP = new HashMap<>(32);
        /**
         * Map with primitive wrapper type as key and corresponding primitive type as value, for example: Integer.class
         * -> int.class.
         */
        private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_TYPE_MAP = new HashMap<>(16);

        static {
            PRIMITIVE_WRAPPER_TYPE_MAP.put(Boolean.class, boolean.class);
            PRIMITIVE_WRAPPER_TYPE_MAP.put(Byte.class, byte.class);
            PRIMITIVE_WRAPPER_TYPE_MAP.put(Character.class, char.class);
            PRIMITIVE_WRAPPER_TYPE_MAP.put(Double.class, double.class);
            PRIMITIVE_WRAPPER_TYPE_MAP.put(Float.class, float.class);
            PRIMITIVE_WRAPPER_TYPE_MAP.put(Integer.class, int.class);
            PRIMITIVE_WRAPPER_TYPE_MAP.put(Long.class, long.class);
            PRIMITIVE_WRAPPER_TYPE_MAP.put(Short.class, short.class);
            PRIMITIVE_WRAPPER_TYPE_MAP.put(Void.class, void.class);

            Set<Class<?>> primitiveTypeNames = new HashSet<>(32);
            primitiveTypeNames.addAll(PRIMITIVE_WRAPPER_TYPE_MAP.values());
            primitiveTypeNames.addAll(Arrays.asList(boolean[].class, byte[].class, char[].class, double[].class,
                    float[].class, int[].class, long[].class, short[].class));
            for (Class<?> primitiveTypeName : primitiveTypeNames) {
                PRIMITIVE_TYPE_NAME_MAP.put(primitiveTypeName.getName(), primitiveTypeName);
            }
        }

        /**
         * get class loader
         *
         * @param clazz
         * @return class loader
         */
        public static ClassLoader getClassLoader(Class<?> clazz) {
            ClassLoader cl = null;
            if (!clazz.getName()
                    .startsWith("org.apache.dubbo")) {
                cl = clazz.getClassLoader();
            }
            if (cl == null) {
                try {
                    cl = Thread.currentThread()
                            .getContextClassLoader();
                } catch (Exception ignored) {
                    // Cannot access thread context ClassLoader - falling back to system class loader...
                }
                if (cl == null) {
                    // No thread context class loader -> use class loader of this class.
                    cl = clazz.getClassLoader();
                    if (cl == null) {
                        // getClassLoader() returning null indicates the bootstrap ClassLoader
                        try {
                            cl = ClassLoader.getSystemClassLoader();
                        } catch (Exception ignored) {
                            // Cannot access system ClassLoader - oh well, maybe the caller can live with null...
                        }
                    }
                }
            }

            return cl;
        }

        /**
         * Return the default ClassLoader to use: typically the thread context ClassLoader, if available; the
         * ClassLoader that loaded the ClassUtils class will be used as fallback.
         * <p>
         * Call this method if you intend to use the thread context ClassLoader in a scenario where you absolutely need
         * a non-null ClassLoader reference: for example, for class path resource loading (but not necessarily for
         * <code>Class.forName</code>, which accepts a <code>null</code> ClassLoader
         * reference as well).
         *
         * @return the default ClassLoader (never <code>null</code>)
         * @see Thread#getContextClassLoader()
         */
        public static ClassLoader getClassLoader() {
            return getClassLoader(ClassUtils.class);
        }

        /**
         * Same as <code>Class.forName()</code>, except that it works for primitive types.
         */
        public static Class<?> forName(String name) throws ClassNotFoundException {
            return forName(name, getClassLoader());
        }

        /**
         * Replacement for <code>Class.forName()</code> that also returns Class instances for primitives (like "int")
         * and array class names (like "String[]").
         *
         * @param name        the name of the Class
         * @param classLoader the class loader to use (may be <code>null</code>, which indicates the default class
         *                    loader)
         * @return Class instance for the supplied name
         * @throws ClassNotFoundException if the class was not found
         * @throws LinkageError           if the class file could not be loaded
         * @see Class#forName(String, boolean, ClassLoader)
         */
        public static Class<?> forName(
                String name,
                ClassLoader classLoader) throws ClassNotFoundException, LinkageError {

            Class<?> clazz = resolvePrimitiveClassName(name);
            if (clazz != null) {
                return clazz;
            }

            // "java.lang.String[]" style arrays
            if (name.endsWith(ARRAY_SUFFIX)) {
                String elementClassName = name.substring(0, name.length() - ARRAY_SUFFIX.length());
                Class<?> elementClass = forName(elementClassName, classLoader);
                return Array.newInstance(elementClass, 0)
                        .getClass();
            }

            // "[Ljava.lang.String;" style arrays
            int internalArrayMarker = name.indexOf(INTERNAL_ARRAY_PREFIX);
            if (internalArrayMarker != -1 && name.endsWith(";")) {
                String elementClassName = null;
                if (internalArrayMarker == 0) {
                    elementClassName = name.substring(INTERNAL_ARRAY_PREFIX.length(), name.length() - 1);
                } else if (name.startsWith("[")) {
                    elementClassName = name.substring(1);
                }
                Class<?> elementClass = forName(elementClassName, classLoader);
                return Array.newInstance(elementClass, 0)
                        .getClass();
            }

            ClassLoader classLoaderToUse = classLoader;
            if (classLoaderToUse == null) {
                classLoaderToUse = getClassLoader();
            }
            return classLoaderToUse.loadClass(name);
        }

        /**
         * Resolve the given class name as primitive class, if appropriate, according to the JVM's naming rules for
         * primitive classes.
         * <p>
         * Also supports the JVM's internal class names for primitive arrays. Does
         * <i>not</i> support the "[]" suffix notation for primitive arrays; this is
         * only supported by {@link #forName}.
         *
         * @param name the name of the potentially primitive class
         * @return the primitive class, or <code>null</code> if the name does not denote a primitive class or primitive
         * array class
         */
        public static Class<?> resolvePrimitiveClassName(String name) {
            Class<?> result = null;
            // Most class names will be quite long, considering that they
            // SHOULD sit in a package, so a length check is worthwhile.
            if (name != null && name.length() <= 8) {
                // Could be a primitive - likely.
                result = (Class<?>) PRIMITIVE_TYPE_NAME_MAP.get(name);
            }
            return result;
        }

    }

}
