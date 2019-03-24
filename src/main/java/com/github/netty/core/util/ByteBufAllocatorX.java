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
        return ByteBufAllocator.DEFAULT.buffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return ByteBufAllocator.DEFAULT.buffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return ByteBufAllocator.DEFAULT.buffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        return ByteBufAllocator.DEFAULT.ioBuffer();
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
        return ByteBufAllocator.DEFAULT.ioBuffer(initialCapacity);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
        return ByteBufAllocator.DEFAULT.ioBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        return ByteBufAllocator.DEFAULT.heapBuffer();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return ByteBufAllocator.DEFAULT.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        return ByteBufAllocator.DEFAULT.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return ByteBufAllocator.DEFAULT.directBuffer();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return ByteBufAllocator.DEFAULT.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return ByteBufAllocator.DEFAULT.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
        return ByteBufAllocator.DEFAULT.compositeBuffer();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
        return ByteBufAllocator.DEFAULT.compositeBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
        return ByteBufAllocator.DEFAULT.compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return ByteBufAllocator.DEFAULT.compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
        return ByteBufAllocator.DEFAULT.compositeDirectBuffer();
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return ByteBufAllocator.DEFAULT.compositeDirectBuffer(maxNumComponents);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return ByteBufAllocator.DEFAULT.isDirectBufferPooled();
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        return ByteBufAllocator.DEFAULT.calculateNewCapacity(minNewCapacity, maxCapacity);
    }

}
