package com.github.netty.protocol.dubbo.serialization;

import com.alibaba.fastjson2.JSONB;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.ContextAutoTypeBeforeHandler;
import com.alibaba.fastjson2.reader.ObjectReaderCreatorASM;
import com.alibaba.fastjson2.util.TypeUtils;
import com.alibaba.fastjson2.writer.ObjectWriterCreatorASM;
import com.github.netty.protocol.dubbo.Serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.fastjson2.util.TypeUtils.loadClass;

/**
 * FastJson serialization implementation
 *
 * <pre>
 *     e.g. &lt;dubbo:protocol serialization="fastjson" /&gt;
 * </pre>
 */
public class FastJson2Serialization implements Serialization {

    static {
        Class<?> aClass = null;
        try {
            aClass = com.alibaba.fastjson2.JSONB.class;
        } catch (Throwable ignored) {
        }
        if (aClass == null) {
//            logger.info("Failed to load com.alibaba.fastjson2.JSONB, fastjson2 serialization will be disabled.");
            throw new IllegalStateException("The fastjson2 is not in classpath.");
        }
    }

    private final byte contentTypeId;
    private final Fastjson2SecurityManager securityManager = new Fastjson2SecurityManager();
    private final Fastjson2CreatorManager creatorManager = new Fastjson2CreatorManager();

    public FastJson2Serialization(byte contentTypeId) {
        this.contentTypeId = contentTypeId;
    }

    public byte getContentTypeId() {
        return contentTypeId;
    }

    @Override
    public Serialization.ObjectOutput serialize(OutputStream output) throws IOException {
        return new FastJson2ObjectOutput(creatorManager, securityManager, output);
    }

    @Override
    public Serialization.ObjectInput deserialize(InputStream input) throws IOException {
        return new FastJson2ObjectInput(creatorManager, securityManager, input);
    }

    public static class Fastjson2SecurityManager implements AllowClassNotifyListener {
        private final SerializeSecurityManager securityManager;
        private volatile Fastjson2SecurityManager.Handler securityFilter;
        private volatile SerializeCheckStatus status = AllowClassNotifyListener.DEFAULT_STATUS;

        private volatile boolean checkSerializable = true;

        private volatile Set<String> allowedList = Collections.newSetFromMap(new ConcurrentHashMap<>(1));

        private volatile Set<String> disAllowedList = Collections.newSetFromMap(new ConcurrentHashMap<>(1));

        public Fastjson2SecurityManager() {
            securityManager = SerializeSecurityManager.INSTANCE;
            securityManager.registerListener(this);
            securityFilter = new Fastjson2SecurityManager.Handler(
                    status,
                    securityManager,
                    true,
                    new String[0],
                    Collections.newSetFromMap(new ConcurrentHashMap<>()));
        }

        @Override
        public synchronized void notifyPrefix(Set<String> allowedList, Set<String> disAllowedList) {
            this.allowedList = allowedList;
            this.disAllowedList = disAllowedList;
            this.securityFilter = new Fastjson2SecurityManager.Handler(
                    this.status,
                    this.securityManager,
                    this.checkSerializable,
                    this.allowedList.toArray(new String[0]),
                    this.disAllowedList);
        }

        @Override
        public synchronized void notifyCheckStatus(SerializeCheckStatus status) {
            this.status = status;
            this.securityFilter = new Fastjson2SecurityManager.Handler(
                    this.status,
                    this.securityManager,
                    this.checkSerializable,
                    this.allowedList.toArray(new String[0]),
                    this.disAllowedList);
        }

        @Override
        public synchronized void notifyCheckSerializable(boolean checkSerializable) {
            this.checkSerializable = checkSerializable;
            this.securityFilter = new Fastjson2SecurityManager.Handler(
                    this.status,
                    this.securityManager,
                    this.checkSerializable,
                    this.allowedList.toArray(new String[0]),
                    this.disAllowedList);
        }

        public Fastjson2SecurityManager.Handler getSecurityFilter() {
            return securityFilter;
        }

        public static class Handler extends ContextAutoTypeBeforeHandler {
            final SerializeCheckStatus status;
            final SerializeSecurityManager serializeSecurityManager;
            final Map<String, Class<?>> classCache = new ConcurrentHashMap<>(16, 0.75f, 1);

            final Set<String> disAllowedList;

            final boolean checkSerializable;

            public Handler(
                    SerializeCheckStatus status,
                    SerializeSecurityManager serializeSecurityManager,
                    boolean checkSerializable,
                    String[] acceptNames,
                    Set<String> disAllowedList) {
                super(true, acceptNames);
                this.status = status;
                this.serializeSecurityManager = serializeSecurityManager;
                this.checkSerializable = checkSerializable;
                this.disAllowedList = disAllowedList;
            }

            @Override
            public Class<?> apply(String typeName, Class<?> expectClass, long features) {
                Class<?> tryLoad = super.apply(typeName, expectClass, features);

                // 1. in allow list, return
                if (tryLoad != null) {
                    return tryLoad;
                }

                // 2. check if in strict mode
                if (status == SerializeCheckStatus.STRICT) {
                    String msg = "[Serialization Security] Serialized class " + typeName + " is not in allow list. "
                            + "Current mode is `STRICT`, will disallow to deserialize it by default. "
                            + "Please add it into security/serialize.allowlist or follow FAQ to configure it.";
                    if (serializeSecurityManager.getWarnedClasses().add(typeName)) {
//                        logger.error(PROTOCOL_UNTRUSTED_SERIALIZE_CLASS, "", "", msg);
                    }

                    throw new IllegalArgumentException(msg);
                }

                // 3. try load
                Class<?> localClass = loadClassDirectly(typeName);
                if (localClass != null) {
                    if (status == SerializeCheckStatus.WARN
                            && serializeSecurityManager.getWarnedClasses().add(typeName)) {
//                        logger.warn(
//                                PROTOCOL_UNTRUSTED_SERIALIZE_CLASS,
//                                "",
//                                "",
//                                "[Serialization Security] Serialized class " + localClass.getName()
//                                        + " is not in allow list. "
//                                        + "Current mode is `WARN`, will allow to deserialize it by default. "
//                                        + "Dubbo will set to `STRICT` mode by default in the future. "
//                                        + "Please add it into security/serialize.allowlist or follow FAQ to configure it.");
                    }
                    return localClass;
                }

                // 4. class not found
                return null;
            }

            public boolean checkIfDisAllow(String typeName) {
                return disAllowedList.stream().anyMatch(typeName::startsWith);
            }

            public boolean isCheckSerializable() {
                return checkSerializable;
            }

            public Class<?> loadClassDirectly(String typeName) {
                Class<?> clazz = classCache.get(typeName);

                if (clazz == null && checkIfDisAllow(typeName)) {
                    clazz = Fastjson2SecurityManager.DenyClass.class;
                    String msg = "[Serialization Security] Serialized class " + typeName + " is in disAllow list. "
                            + "Current mode is `WARN`, will disallow to deserialize it by default. "
                            + "Please add it into security/serialize.allowlist or follow FAQ to configure it.";
                    if (serializeSecurityManager.getWarnedClasses().add(typeName)) {
//                        logger.warn(PROTOCOL_UNTRUSTED_SERIALIZE_CLASS, "", "", msg);
                    }
                }

                if (clazz == null) {
                    clazz = TypeUtils.getMapping(typeName);
                }

                if (clazz == null) {
                    clazz = loadClass(typeName);
                }

                if (clazz != null) {
                    Class<?> origin = classCache.putIfAbsent(typeName, clazz);
                    if (origin != null) {
                        clazz = origin;
                    }
                }

                if (clazz == Fastjson2SecurityManager.DenyClass.class) {
                    return null;
                }

                return clazz;
            }
        }

        private static class DenyClass {
            // To indicate that the target class has been reject
        }
    }

    /**
     * FastJson object input implementation
     */
    public static class FastJson2ObjectInput implements Serialization.ObjectInput {

        private final Fastjson2CreatorManager fastjson2CreatorManager;

        private final Fastjson2SecurityManager fastjson2SecurityManager;
        private final InputStream is;
        private volatile ClassLoader classLoader;

        public FastJson2ObjectInput(
                Fastjson2CreatorManager fastjson2CreatorManager,
                Fastjson2SecurityManager fastjson2SecurityManager,
                InputStream in) {
            this.fastjson2CreatorManager = fastjson2CreatorManager;
            this.fastjson2SecurityManager = fastjson2SecurityManager;
            this.classLoader = Thread.currentThread().getContextClassLoader();
            this.is = in;
            fastjson2CreatorManager.setCreator(classLoader);
        }

        @Override
        public String readUTF() throws IOException {
            return readObject(String.class);
        }

        @Override
        public void cleanup() {

        }

        @Override
        public long skip(long n) throws IOException {
            return is.skip(Math.min(is.available(), n));
        }

        @Override
        public Object readObject() throws IOException, ClassNotFoundException {
            return readObject(Object.class);
        }

        @Override
        public <T> T readObject(Class<T> cls) throws IOException {
            updateClassLoaderIfNeed();
            int length = readLength();
            byte[] bytes = new byte[length];
            int read = is.read(bytes, 0, length);
            if (read != length) {
                throw new IllegalArgumentException(
                        "deserialize failed. expected read length: " + length + " but actual read: " + read);
            }
            Fastjson2SecurityManager.Handler securityFilter = fastjson2SecurityManager.getSecurityFilter();
            T result;
            if (securityFilter.isCheckSerializable()) {
                result = JSONB.parseObject(
                        bytes,
                        cls,
                        securityFilter,
                        JSONReader.Feature.UseDefaultConstructorAsPossible,
                        JSONReader.Feature.ErrorOnNoneSerializable,
                        JSONReader.Feature.IgnoreAutoTypeNotMatch,
                        JSONReader.Feature.UseNativeObject,
                        JSONReader.Feature.FieldBased);
            } else {
                result = JSONB.parseObject(
                        bytes,
                        cls,
                        securityFilter,
                        JSONReader.Feature.UseDefaultConstructorAsPossible,
                        JSONReader.Feature.UseNativeObject,
                        JSONReader.Feature.IgnoreAutoTypeNotMatch,
                        JSONReader.Feature.FieldBased);
            }
//            if (result != null && cls != null && !ClassUtils.isMatch(result.getClass(), cls)) {
//                throw new IllegalArgumentException(
//                        "deserialize failed. expected class: " + cls + " but actual class: " + result.getClass());
//            }
            return result;
        }

        private void updateClassLoaderIfNeed() {
            ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
            if (currentClassLoader != classLoader) {
                fastjson2CreatorManager.setCreator(currentClassLoader);
                classLoader = currentClassLoader;
            }
        }

        private int readLength() throws IOException {
            byte[] bytes = new byte[Integer.BYTES];
            int read = is.read(bytes, 0, Integer.BYTES);
            if (read != Integer.BYTES) {
                throw new IllegalArgumentException(
                        "deserialize failed. expected read length: " + Integer.BYTES + " but actual read: " + read);
            }
            int value = 0;
            for (byte b : bytes) {
                value = (value << 8) + (b & 0xFF);
            }
            return value;
        }
    }

    public static class Fastjson2CreatorManager {

        /**
         * An empty classLoader used when classLoader is system classLoader. Prevent the NPE.
         */
        private static final ClassLoader SYSTEM_CLASSLOADER_KEY = new ClassLoader() {
        };

        private final Map<ClassLoader, ObjectReaderCreatorASM> readerMap = new ConcurrentHashMap<>();
        private final Map<ClassLoader, ObjectWriterCreatorASM> writerMap = new ConcurrentHashMap<>();

        public Fastjson2CreatorManager() {
        }

        public void setCreator(ClassLoader classLoader) {
            if (classLoader == null) {
                classLoader = SYSTEM_CLASSLOADER_KEY;
            }
            JSONFactory.setContextReaderCreator(readerMap.computeIfAbsent(classLoader, ObjectReaderCreatorASM::new));
            JSONFactory.setContextWriterCreator(writerMap.computeIfAbsent(classLoader, ObjectWriterCreatorASM::new));
        }
    }

    /**
     * FastJson object output implementation
     */
    public class FastJson2ObjectOutput implements Serialization.ObjectOutput {

        private final Fastjson2CreatorManager fastjson2CreatorManager;

        private final Fastjson2SecurityManager fastjson2SecurityManager;
        private final OutputStream os;
        private volatile ClassLoader classLoader;

        public FastJson2ObjectOutput(
                Fastjson2CreatorManager fastjson2CreatorManager,
                Fastjson2SecurityManager fastjson2SecurityManager,
                OutputStream out) {
            this.fastjson2CreatorManager = fastjson2CreatorManager;
            this.fastjson2SecurityManager = fastjson2SecurityManager;
            this.classLoader = Thread.currentThread().getContextClassLoader();
            this.os = out;
            fastjson2CreatorManager.setCreator(classLoader);
        }

        @Override
        public void writeObject(Object obj) throws IOException {
            updateClassLoaderIfNeed();
            byte[] bytes;
            if (fastjson2SecurityManager.getSecurityFilter().isCheckSerializable()) {
                bytes = JSONB.toBytes(
                        obj,
                        JSONWriter.Feature.WriteClassName,
                        JSONWriter.Feature.FieldBased,
                        JSONWriter.Feature.ErrorOnNoneSerializable,
                        JSONWriter.Feature.ReferenceDetection,
                        JSONWriter.Feature.WriteNulls,
                        JSONWriter.Feature.NotWriteDefaultValue,
                        JSONWriter.Feature.NotWriteHashMapArrayListClassName,
                        JSONWriter.Feature.WriteNameAsSymbol);
            } else {
                bytes = JSONB.toBytes(
                        obj,
                        JSONWriter.Feature.WriteClassName,
                        JSONWriter.Feature.FieldBased,
                        JSONWriter.Feature.ReferenceDetection,
                        JSONWriter.Feature.WriteNulls,
                        JSONWriter.Feature.NotWriteDefaultValue,
                        JSONWriter.Feature.NotWriteHashMapArrayListClassName,
                        JSONWriter.Feature.WriteNameAsSymbol);
            }
            writeLength(bytes.length);
            os.write(bytes);
            os.flush();
        }

        @Override
        public void writeUTF(String obj) throws IOException {
            writeObject(obj);
        }

        private void updateClassLoaderIfNeed() {
            ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
            if (currentClassLoader != classLoader) {
                fastjson2CreatorManager.setCreator(currentClassLoader);
                classLoader = currentClassLoader;
            }
        }

        private void writeLength(int value) throws IOException {
            byte[] bytes = new byte[Integer.BYTES];
            int length = bytes.length;
            for (int i = 0; i < length; i++) {
                bytes[length - i - 1] = (byte) (value & 0xFF);
                value >>= 8;
            }
            os.write(bytes);
        }

        @Override
        public void flushBuffer() throws IOException {
            os.flush();
        }

        @Override
        public void cleanup() {

        }
    }
}
