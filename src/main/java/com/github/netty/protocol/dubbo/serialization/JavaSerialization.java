package com.github.netty.protocol.dubbo.serialization;

import com.github.netty.protocol.dubbo.Serialization;

import java.io.*;

/**
 * Java serialization implementation
 *
 * <pre>
 *     e.g. &lt;dubbo:protocol serialization="java" /&gt;
 * </pre>
 */

public class JavaSerialization implements Serialization {
    private final byte contentTypeId;

    public JavaSerialization(byte contentTypeId) {
        this.contentTypeId = contentTypeId;
    }

    @Override
    public byte getContentTypeId() {
        return contentTypeId;
    }

    @Override
    public ObjectOutput serialize(OutputStream out) throws IOException {
        return new JavaObjectOutput(out);
    }

    @Override
    public ObjectInput deserialize(InputStream is) throws IOException {
        return new JavaObjectInput(is);
    }

    /**
     * Compacted java object input implementation
     */
    public static class CompactedObjectInputStream extends ObjectInputStream {
        private final ClassLoader mClassLoader;

        public CompactedObjectInputStream(InputStream in) throws IOException {
            this(in, Thread.currentThread().getContextClassLoader());
        }

        public CompactedObjectInputStream(InputStream in, ClassLoader cl) throws IOException {
            super(in);
            mClassLoader = cl == null ? getClassLoader(CompactedObjectInputStream.class) : cl;
        }

        private static ClassLoader getClassLoader(Class<?> clazz) {
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

        @Override
        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            int type = read();
            if (type < 0) {
                throw new EOFException();
            }
            switch (type) {
                case 0:
                    return super.readClassDescriptor();
                case 1:
                    Class<?> clazz = loadClass(readUTF());
                    return ObjectStreamClass.lookup(clazz);
                default:
                    throw new StreamCorruptedException("Unexpected class descriptor type: " + type);
            }
        }

        private Class<?> loadClass(String className) throws ClassNotFoundException {
            return mClassLoader.loadClass(className);
        }
    }

    public static class JavaObjectInput extends NativeJavaSerialization.NativeJavaObjectInput {
        public JavaObjectInput(InputStream is) throws IOException {
            super(new ObjectInputStream(is));
        }

        public JavaObjectInput(InputStream is, boolean compacted) throws IOException {
            super(compacted ? new CompactedObjectInputStream(is) : new ObjectInputStream(is));
        }

        @Override
        public String readUTF() throws IOException {
            int len = getObjectInputStream().readInt();
            if (len < 0) {
                return null;
            }

            return getObjectInputStream().readUTF();
        }

        @Override
        public Object readObject() throws IOException, ClassNotFoundException {
            byte b = getObjectInputStream().readByte();
            if (b == 0) {
                return null;
            }

            return getObjectInputStream().readObject();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T readObject(Class<T> cls) throws IOException, ClassNotFoundException {
            return (T) readObject();
        }
    }

    public static class CompactedObjectOutputStream extends ObjectOutputStream {
        public CompactedObjectOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
            Class<?> clazz = desc.forClass();
            if (clazz.isPrimitive() || clazz.isArray()) {
                write(0);
                super.writeClassDescriptor(desc);
            } else {
                write(1);
                writeUTF(desc.getName());
            }
        }
    }

    /**
     * Java object output implementation
     */
    public static class JavaObjectOutput extends NativeJavaSerialization.NativeJavaObjectOutput {
        public JavaObjectOutput(OutputStream os) throws IOException {
            super(new ObjectOutputStream(os));
        }

        public JavaObjectOutput(OutputStream os, boolean compact) throws IOException {
            super(compact ? new CompactedObjectOutputStream(os) : new ObjectOutputStream(os));
        }


        @Override
        public void writeObject(Object obj) throws IOException {
            if (obj == null) {
                getObjectOutputStream().writeByte(0);
            } else {
                getObjectOutputStream().writeByte(1);
                getObjectOutputStream().writeObject(obj);
            }
        }

        @Override
        public void writeUTF(String obj) throws IOException {
            if (obj == null) {
                getObjectOutputStream().writeInt(-1);
            } else {
                getObjectOutputStream().writeInt(obj.length());
                getObjectOutputStream().writeUTF(obj);
            }
        }

        @Override
        public void flushBuffer() throws IOException {
            getObjectOutputStream().flush();
        }
    }

}