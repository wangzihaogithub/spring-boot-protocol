package com.github.netty.core.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

/**
 * Combined byte buffer
 *
 * @author wangzihao
 *  2018/8/11/011
 */
public class CompositeByteBufX extends CompositeByteBuf {

    /**
     * Commonly used maximum number of bytes     4096 * 32 = 128 KB
     */
    public CompositeByteBufX() {
        super(ByteBufAllocatorX.INSTANCE, true, 32);
    }

    public CompositeByteBufX(boolean direct, int maxNumComponents) {
        super(ByteBufAllocatorX.INSTANCE, direct, maxNumComponents);
    }

    public CompositeByteBufX(ByteBufAllocator alloc, boolean direct, int maxNumComponents) {
        super(alloc, direct, maxNumComponents);
    }

    public CompositeByteBufX(ByteBufAllocator alloc, boolean direct, int maxNumComponents, ByteBuf... buffers) {
        super(alloc, direct, maxNumComponents, buffers);
    }

    public CompositeByteBufX(ByteBufAllocator alloc, boolean direct, int maxNumComponents, Iterable<ByteBuf> buffers) {
        super(alloc, direct, maxNumComponents, buffers);
    }

}
