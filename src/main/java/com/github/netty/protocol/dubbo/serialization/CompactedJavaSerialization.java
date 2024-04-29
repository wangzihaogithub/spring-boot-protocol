package com.github.netty.protocol.dubbo.serialization;

import com.github.netty.protocol.dubbo.Serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CompactedJavaSerialization implements Serialization {
    private final byte contentTypeId;

    public CompactedJavaSerialization(byte contentTypeId) {
        this.contentTypeId = contentTypeId;
    }

    @Override
    public byte getContentTypeId() {
        return contentTypeId;
    }

    @Override
    public ObjectOutput serialize(OutputStream output) throws IOException {
        return new JavaSerialization.JavaObjectOutput(output, true);
    }

    @Override
    public ObjectInput deserialize(InputStream input) throws IOException {
        return new JavaSerialization.JavaObjectInput(input, true);
    }

}