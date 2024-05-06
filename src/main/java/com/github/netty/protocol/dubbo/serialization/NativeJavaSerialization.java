package com.github.netty.protocol.dubbo.serialization;

import com.github.netty.protocol.dubbo.Serialization;

import java.io.*;
import java.util.Objects;

/**
 * Native java serialization implementation
 *
 * <pre>
 *     e.g. &lt;dubbo:protocol serialization="nativejava" /&gt;
 * </pre>
 */
public class NativeJavaSerialization implements Serialization {
    private final byte contentTypeId;

    public NativeJavaSerialization(byte contentTypeId) {
        this.contentTypeId = contentTypeId;
    }

    @Override
    public byte getContentTypeId() {
        return contentTypeId;
    }

    @Override
    public ObjectOutput serialize(OutputStream output) throws IOException {
        return new NativeJavaObjectOutput(output);
    }

    @Override
    public ObjectInput deserialize(InputStream input) throws IOException {
        return new NativeJavaObjectInput(input);
    }

    /**
     * Native java object input implementation
     */
    public static class NativeJavaObjectInput implements ObjectInput {

        private final ObjectInputStream inputStream;

        public NativeJavaObjectInput(InputStream is) throws IOException {
            this(new ObjectInputStream(is));
        }

        protected NativeJavaObjectInput(ObjectInputStream is) {
            Objects.requireNonNull(is, "input == null");
            inputStream = is;
        }

        protected ObjectInputStream getObjectInputStream() {
            return inputStream;
        }

        @Override
        public Object readObject() throws IOException, ClassNotFoundException {
            return inputStream.readObject();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T readObject(Class<T> cls) throws IOException, ClassNotFoundException {
            return (T) readObject();
        }

        @Override
        public String readUTF() throws IOException {
            return inputStream.readUTF();
        }

        @Override
        public void cleanup() {

        }

        @Override
        public long skip(long n) throws IOException {
            return inputStream.skip(Math.min(inputStream.available(), n));
        }
    }

    /**
     * Native java object output implementation
     */
    public static class NativeJavaObjectOutput implements ObjectOutput {

        private final ObjectOutputStream outputStream;

        public NativeJavaObjectOutput(OutputStream os) throws IOException {
            this(new ObjectOutputStream(os));
        }

        protected NativeJavaObjectOutput(ObjectOutputStream out) {
            Objects.requireNonNull(out, "output == null");
            this.outputStream = out;
        }

        protected ObjectOutputStream getObjectOutputStream() {
            return outputStream;
        }

        @Override
        public void writeObject(Object obj) throws IOException {
            outputStream.writeObject(obj);
        }

        @Override
        public void writeUTF(String obj) throws IOException {
            outputStream.writeUTF(obj);
        }

        @Override
        public void flushBuffer() throws IOException {
            outputStream.flush();
        }

        @Override
        public void cleanup() {

        }
    }

}
