package com.github.netty.core.util;

import io.netty.buffer.*;

/**
 * Created by wangzihao on 2018/8/5/005.

 * @author wangzihao
 */
public class ByteBufAllocatorX implements ByteBufAllocator {
    public static final ByteBufAllocator POOLED = PooledByteBufAllocator.DEFAULT;
    public static final ByteBufAllocator UNPOOLED = UnpooledByteBufAllocator.DEFAULT;
    public static final ByteBufAllocatorX INSTANCE = new ByteBufAllocatorX();

    private ByteBufAllocatorX() { }

    @Override
    public ByteBuf buffer() {
        return DEFAULT.buffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return DEFAULT.buffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return DEFAULT.buffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        return DEFAULT.ioBuffer();
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
        return DEFAULT.ioBuffer(initialCapacity);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
        return DEFAULT.ioBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        return DEFAULT.heapBuffer();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return POOLED.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        return DEFAULT.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return DEFAULT.directBuffer();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return DEFAULT.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return DEFAULT.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
        return DEFAULT.compositeBuffer();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
        return DEFAULT.compositeBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
        return DEFAULT.compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return DEFAULT.compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
        return DEFAULT.compositeDirectBuffer();
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return DEFAULT.compositeDirectBuffer(maxNumComponents);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return DEFAULT.isDirectBufferPooled();
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        return DEFAULT.calculateNewCapacity(minNewCapacity, maxCapacity);
    }

}
