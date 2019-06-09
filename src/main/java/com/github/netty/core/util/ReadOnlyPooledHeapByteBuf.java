package com.github.netty.core.util;

import io.netty.buffer.*;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * ReadOnlyPooledHeapByteBuf
 * @author wangzihao
 */
class ReadOnlyPooledHeapByteBuf extends AbstractReferenceCountedByteBuf {
    private byte[] array;
    private ByteBuffer tmpNioBuf;
    private int offset;
    private int capacity;
    private static final Recycler<ReadOnlyPooledHeapByteBuf> RECYCLER = new Recycler<>(ReadOnlyPooledHeapByteBuf::new);
    private ByteBuf parent;

    private ReadOnlyPooledHeapByteBuf() {
        super(0);
        this.array = EmptyArrays.EMPTY_BYTES;
        this.offset = 0;
    }

    static ReadOnlyPooledHeapByteBuf newInstance(byte[] bytes) {
        ReadOnlyPooledHeapByteBuf instance = RECYCLER.getInstance();
        instance.setRefCnt(1);
        instance.maxCapacity(bytes.length);
        instance.capacity = bytes.length;
        instance.setArray(bytes);
        instance.setIndex(0,bytes.length);
        return instance;
    }

    private int idx(int index) {
        return offset + index;
    }

    @Override
    public ByteBuf slice(int index, int length) {
        checkIndex(index, length);
        if(maxCapacity() < index+length) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d, length: %d (expected: range(0, %d))", index, length, maxCapacity()));
        }
        ReadOnlyPooledHeapByteBuf slice = newInstance(array);
        slice.maxCapacity(length);
        slice.capacity = length;
        slice.setIndex(0,length);
        slice.parent = this;
        slice.offset = offset + index;
        return slice;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public boolean isWritable(int numBytes) {
        return false;
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        return 1;
    }

    @Override
    public ByteBuf ensureWritable(int minWritableBytes) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public byte[] array() {
        return array;
    }

    @Override
    public ByteBuf discardReadBytes() {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setByte(int index, int value) {

    }

    @Override
    public ByteBuf setShort(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setShort(int index, int value) {

    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setShortLE(int index, int value) {

    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setMedium(int index, int value) {

    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setMediumLE(int index, int value) {

    }

    @Override
    public ByteBuf setInt(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setInt(int index, int value) {

    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setIntLE(int index, int value) {

    }

    @Override
    public ByteBuf setLong(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setLong(int index, long value) {

    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected void _setLongLE(int index, long value) {

    }

    @Override
    public int setBytes(int index, InputStream in, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf duplicate() {
        return copy();
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public ByteBuf asReadOnly() {
        return this;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    byte[] allocateArray(int initialCapacity) {
        return new byte[initialCapacity];
    }

    void freeArray(byte[] array) {
        // NOOP
    }

    private void setArray(byte[] initialArray) {
        array = initialArray;
        tmpNioBuf = null;
    }

    @Override
    public ByteBufAllocator alloc() {
        return ByteBufAllocatorX.INSTANCE;
    }

    @Override
    public ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }

    @Override
    public final boolean isDirect() {
        return false;
    }

    @Override
    protected byte _getByte(int index) {
        return IOUtil.getByte(array, idx(index));
    }

    @Override
    protected short _getShort(int index) {
        return IOUtil.getShort(array, idx(index));
    }

    @Override
    protected short _getShortLE(int index) {
        return IOUtil.getShortLE(array, idx(index));
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return IOUtil.getUnsignedMedium(array, idx(index));
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return IOUtil.getUnsignedMediumLE(array, idx(index));
    }

    @Override
    protected int _getInt(int index) {
        return IOUtil.getInt(array, idx(index));
    }

    @Override
    protected int _getIntLE(int index) {
        return IOUtil.getIntLE(array, idx(index));
    }

    @Override
    protected long _getLong(int index) {
        return IOUtil.getLong(array, idx(index));
    }

    @Override
    protected long _getLongLE(int index) {
        return IOUtil.getLongLE(array, idx(index));
    }

    @Override
    public final ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasMemoryAddress()) {
            PlatformDependent.copyMemory(array, idx(index), dst.memoryAddress() + dstIndex, length);
        } else if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            dst.setBytes(dstIndex, array, idx(index), length);
        }
        return this;
    }

    @Override
    public final ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        System.arraycopy(array, idx(index), dst, dstIndex, length);
        return this;
    }

    @Override
    public final ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());
        dst.put(array, idx(index), dst.remaining());
        return this;
    }

    @Override
    public final ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        checkIndex(index, length);
        out.write(array, idx(index), length);
        return this;
    }

    @Override
    public final int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return getBytes(index, out, length, false);
    }

    private int getBytes(int index, GatheringByteChannel out, int length, boolean internal) throws IOException {
        checkIndex(index, length);
        index = idx(index);
        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = internalNioBuffer();
        } else {
            tmpBuf = ByteBuffer.wrap(array);
        }
        return out.write((ByteBuffer) tmpBuf.clear().position(index).limit(index + length));
    }

    @Override
    public final int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return getBytes(index, out, position, length, false);
    }

    private int getBytes(int index, FileChannel out, long position, int length, boolean internal) throws IOException {
        checkIndex(index, length);
        index = idx(index);
        ByteBuffer tmpBuf = internal ? internalNioBuffer() : ByteBuffer.wrap(array);
        return out.write((ByteBuffer) tmpBuf.clear().position(index).limit(index + length), position);
    }

    @Override
    public final ByteBuf copy(int index, int length) {
        return slice(index,length);
    }

    @Override
    public ByteBuf copy() {
        ReadOnlyPooledHeapByteBuf copy = newInstance(array);
        copy.maxCapacity(maxCapacity());
        copy.capacity = capacity;
        copy.setIndex(readerIndex(),writerIndex());
        copy.parent = this;
        copy.offset = offset;
        return copy;
    }

    @Override
    public final int nioBufferCount() {
        return 1;
    }

    @Override
    public final ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
    }

    @Override
    public final ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        index = idx(index);
        ByteBuffer buf =  ByteBuffer.wrap(array, index, length);
        return buf.slice();
    }

    @Override
    public final ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        index = idx(index);
        return (ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length);
    }

    @Override
    public final boolean hasArray() {
        return true;
    }

    @Override
    public final int arrayOffset() {
        return offset;
    }

    @Override
    public final boolean hasMemoryAddress() {
        return false;
    }

    @Override
    public final long memoryAddress() {
        throw new UnsupportedOperationException();
    }

    protected final ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = ByteBuffer.wrap(array);
        }
        return tmpNioBuf;
    }

    @Override
    protected void deallocate() {
        freeArray(array);
        array = EmptyArrays.EMPTY_BYTES;
        parent = null;
        offset = 0;
        RECYCLER.recycleInstance(this);
    }


    public static void main(String[] args) {

        ByteBuf directBuffer = ByteBufAllocatorX.POOLED.directBuffer(30);
        directBuffer.writeBytes(new byte[]{1,2,3,4,5,6,7,8});

        Set<ByteBuf> set = new HashSet<>();
        for(int i=0; i< 30; i++) {
            set.add(directBuffer.copy());
        }



        long time = System.currentTimeMillis();
        ByteBuf byteBuf = Unpooled.copyLong(time);

        long c = byteBuf.getLong(0);



        byte[] requestIdBytes = "requestId".getBytes();
        byte[] requestIdBytesRead = new byte[9];
        ByteBuf byteBuf1 = newInstance(requestIdBytes);

        ByteBuf b1 = byteBuf1.copy();
        ByteBuf b2 = b1.slice(1,2);
        ByteBuf b3 = b2.copy();
        ByteBuf b4 = b3.copy(0,2);



        byteBuf1.readBytes(requestIdBytesRead);
        byteBuf1.release();

        System.out.println("requestIdBytesRead = "+ Arrays.toString(requestIdBytesRead));

        byte[] helloBytes = "hello".getBytes();
        byte[] helloBytesRead = new byte[3];
        ByteBuf byteBuf2 = newInstance(helloBytes);
        byteBuf2.readBytes(helloBytesRead);
        byteBuf2.release();

        System.out.println("helloBytesRead = "+ Arrays.toString(helloBytesRead));
    }

    @Override
    public String toString() {
        return super.toString()+"("+toString(Charset.defaultCharset())+")";
    }
}
