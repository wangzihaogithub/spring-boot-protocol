package com.github.netty.protocol.dubbo.serialization;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;

public class DefaultSerializeClassChecker implements AllowClassNotifyListener {
    public static final DefaultSerializeClassChecker INSTANCE = new DefaultSerializeClassChecker(SerializeSecurityManager.INSTANCE);
    private static final long MAGIC_HASH_CODE = 0xcbf29ce484222325L;
    private static final long MAGIC_PRIME = 0x100000001b3L;
    private final SerializeSecurityManager serializeSecurityManager;
    //        private static final ErrorTypeAwareLogger logger =
    //                LoggerFactory.getErrorTypeAwareLogger(DefaultSerializeClassChecker.class);
    private volatile SerializeCheckStatus checkStatus = AllowClassNotifyListener.DEFAULT_STATUS;
    private volatile boolean checkSerializable = true;
    private volatile long[] allowPrefixes = new long[0];

    private volatile long[] disAllowPrefixes = new long[0];

    public DefaultSerializeClassChecker(SerializeSecurityManager manager) {
        serializeSecurityManager = manager;
        serializeSecurityManager.registerListener(this);
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


    @Override
    public synchronized void notifyPrefix(Set<String> allowedList, Set<String> disAllowedList) {
        this.allowPrefixes = loadPrefix(allowedList);
        this.disAllowPrefixes = loadPrefix(disAllowedList);
    }

    @Override
    public synchronized void notifyCheckStatus(SerializeCheckStatus status) {
        this.checkStatus = status;
    }


    @Override
    public synchronized void notifyCheckSerializable(boolean checkSerializable) {
        this.checkSerializable = checkSerializable;
    }

    /**
     * Try load class
     *
     * @param className class name
     * @throws IllegalArgumentException if class is blocked
     * @return Class
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
            return getClassLoader(Hessian2FactoryManager.class);
        }

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
                result = PRIMITIVE_TYPE_NAME_MAP.get(name);
            }
            return result;
        }

    }
}
