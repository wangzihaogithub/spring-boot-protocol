package com.github.netty.protocol.dubbo.serialization;

import com.alibaba.com.caucho.hessian.io.Hessian2Input;
import com.alibaba.com.caucho.hessian.io.Hessian2Output;
import com.github.netty.protocol.dubbo.Serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

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
        private final LazyHessian2Input hessian2Input;
        private final InputStream inputStream;
        private String lastRead;

        public Hessian2ObjectInput(InputStream inputStream) {
            this.inputStream = inputStream;
            LazyHessian2Input hessian2Input = new LazyHessian2Input(inputStream);
            hessian2Input.setSerializerFactory(FACTORY_MANAGER.getSerializerFactory(
                    Thread.currentThread().getContextClassLoader()));
            this.hessian2Input = hessian2Input;
        }

        @Override
        public Object readArg() throws IOException, ClassNotFoundException {
            Object o = readObject();
            this.lastRead = "readArg";
            return o;
        }

        @Override
        public Object readObject() throws IOException, ClassNotFoundException {
            if (!hessian2Input.getSerializerFactory()
                    .getClassLoader()
                    .equals(Thread.currentThread().getContextClassLoader())) {
                hessian2Input.setSerializerFactory(FACTORY_MANAGER.getSerializerFactory(
                        Thread.currentThread().getContextClassLoader()));
            }
            this.lastRead = "readObject";
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
            this.lastRead = "readObject";
            return (T) hessian2Input.readObject(cls);
        }

        @Override
        public Map<String, Object> readAttachments() throws IOException, ClassNotFoundException {
            if (!hessian2Input.getSerializerFactory()
                    .getClassLoader()
                    .equals(Thread.currentThread().getContextClassLoader())) {
                hessian2Input.setSerializerFactory(FACTORY_MANAGER.getSerializerFactory(
                        Thread.currentThread().getContextClassLoader()));
            }
            this.lastRead = "readAttachments";
            return (Map<String, Object>) hessian2Input.readObject(Hessian2FactoryManager.LazyMapDeserializer.LazyMap.class);
        }

        @Override
        public String readUTF() throws IOException {
            this.lastRead = "readUTF";
            return hessian2Input.readString();
        }

        @Override
        public void close() throws IOException {
            if ("readAttachments".equals(lastRead)) {
                return;
            }
            ObjectInput.super.close();
        }

        @Override
        public void cleanup() {
            hessian2Input.reset();
        }

        @Override
        public long skip(long n) throws IOException {
            return inputStream.skip(Math.min(inputStream.available(), n));
        }

        /**
         * todo， 目前没有切入点能改成lazy
         */
        public static class LazyHessian2Input extends Hessian2Input {
            public LazyHessian2Input(InputStream is) {
                super(is);
            }
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
