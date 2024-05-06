package com.github.netty.protocol.dubbo.serialization;

import com.alibaba.com.caucho.hessian.io.Hessian2Input;
import com.alibaba.com.caucho.hessian.io.Hessian2Output;
import com.github.netty.protocol.dubbo.Serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Hessian2Serialization implements Serialization {
    private static final Hessian2FactoryManager FACTORY_MANAGER = new Hessian2FactoryManager();
    private final byte contentTypeId;

    public Hessian2Serialization(byte contentTypeId) {
        this.contentTypeId = contentTypeId;
    }

    @Override
    public byte getContentTypeId() {
        return contentTypeId;
    }

    @Override
    public ObjectOutput serialize(OutputStream output) throws IOException {
        return new Hessian2ObjectOutput(output);
    }

    @Override
    public ObjectInput deserialize(InputStream input) throws IOException {
        return new Hessian2ObjectInput(input);
    }

    public static class Hessian2ObjectInput implements Serialization.ObjectInput {
        private final Hessian2Input hessian2Input;
        private final InputStream inputStream;

        public Hessian2ObjectInput(InputStream inputStream) {
            this.inputStream = inputStream;
            Hessian2Input hessian2Input = new Hessian2Input(inputStream);
            hessian2Input.setSerializerFactory(FACTORY_MANAGER.getSerializerFactory(
                    Thread.currentThread().getContextClassLoader()));
            this.hessian2Input = hessian2Input;
        }

        @Override
        public Object readObject() throws IOException, ClassNotFoundException {
            if (!hessian2Input.getSerializerFactory()
                    .getClassLoader()
                    .equals(Thread.currentThread().getContextClassLoader())) {
                hessian2Input.setSerializerFactory(FACTORY_MANAGER.getSerializerFactory(
                        Thread.currentThread().getContextClassLoader()));
            }
            return hessian2Input.readObject();
        }

        @Override
        public <T> T readObject(Class<T> cls) throws IOException, ClassNotFoundException {
            if (!hessian2Input.getSerializerFactory()
                    .getClassLoader()
                    .equals(Thread.currentThread().getContextClassLoader())) {
                hessian2Input.setSerializerFactory(FACTORY_MANAGER.getSerializerFactory(
                        Thread.currentThread().getContextClassLoader()));
            }
            return (T) hessian2Input.readObject(cls);
        }

        @Override
        public String readUTF() throws IOException {
            return hessian2Input.readString();
        }

        @Override
        public void cleanup() {
            hessian2Input.reset();
        }

        @Override
        public long skip(long n) throws IOException {
            return inputStream.skip(Math.min(inputStream.available(), n));
        }
    }

    public static class Hessian2ObjectOutput implements Serialization.ObjectOutput {
        private final Hessian2Output hessian2Input;

        public Hessian2ObjectOutput(OutputStream outputStream) {
            Hessian2Output hessian2Input = new Hessian2Output(outputStream);
            hessian2Input.setSerializerFactory(FACTORY_MANAGER.getSerializerFactory(
                    Thread.currentThread().getContextClassLoader()));
            this.hessian2Input = hessian2Input;
        }

        @Override
        public void writeObject(Object obj) throws IOException {
            hessian2Input.writeObject(obj);
        }

        @Override
        public void writeUTF(String obj) throws IOException {
            hessian2Input.writeString(obj);
        }

        @Override
        public void flushBuffer() throws IOException {
            hessian2Input.flushBuffer();
        }

        @Override
        public void cleanup() {
            hessian2Input.reset();
        }

    }

}
