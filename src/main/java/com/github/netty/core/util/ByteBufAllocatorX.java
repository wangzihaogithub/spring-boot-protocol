package com.github.netty.core.util;

/**
 * Created by acer01 on 2018/8/5/005.
 */
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;

import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.util.Objects;

/**
 * A {@link ByteBufAllocator} which is partial pooled. Which means only direct {@link ByteBuf}s are pooled. The rest
 * is unpooled.
 *
 * @author a href="mailto:nmaurer@redhat.com" Norman Maurer
 */
public class ByteBufAllocatorX implements ByteBufAllocator {
    // Make sure we use the same number of areas as EventLoop's to reduce condition.
    // We can remove this once the following netty issue is fixed:
    // See https://github.com/netty/netty/issues/2264

    //池化的堆外内存分配器
    private static final ByteBufAllocator POOLED_DIRECT = new PooledByteBufAllocator(true);
    //非池化的堆内内存分配器
    private static final ByteBufAllocator UNPOOLED_UNDIRECT = new UnpooledByteBufAllocator(false);

    public static final ByteBufAllocatorX INSTANCE = new ByteBufAllocatorX();

    private ByteBufAllocatorX() { }

    @Override
    public ByteBuf buffer() {
        return UNPOOLED_UNDIRECT.heapBuffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return UNPOOLED_UNDIRECT.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return UNPOOLED_UNDIRECT.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        return POOLED_DIRECT.directBuffer();
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
        return POOLED_DIRECT.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
        return POOLED_DIRECT.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        return UNPOOLED_UNDIRECT.heapBuffer();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return UNPOOLED_UNDIRECT.heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        return UNPOOLED_UNDIRECT.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return POOLED_DIRECT.directBuffer();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return POOLED_DIRECT.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return POOLED_DIRECT.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
        return UNPOOLED_UNDIRECT.compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
        return UNPOOLED_UNDIRECT.compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
        return UNPOOLED_UNDIRECT.compositeHeapBuffer();
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return UNPOOLED_UNDIRECT.compositeHeapBuffer(maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
        return POOLED_DIRECT.compositeDirectBuffer();
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return POOLED_DIRECT.compositeDirectBuffer();
    }

    @Override
    public boolean isDirectBufferPooled() {
        return true;
    }

    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        Method method = ReflectUtil.getAccessibleMethod(POOLED_DIRECT.getClass(), "calculateNewCapacity",int.class,int.class);
        try {
            return (int) method.invoke(POOLED_DIRECT,minNewCapacity,maxCapacity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a new {@link ChannelHandlerContext} which wraps the given one anf force the usage of direct buffers.
     */
    public static ChannelHandlerContext forceDirectAllocator(ChannelHandlerContext ctx) {
        return PooledChannelHandlerContext.newInstance(ctx);
    }

    private static class PooledChannelHandlerContext implements ChannelHandlerContext,Wrapper<ChannelHandlerContext>,Recyclable {
        private ChannelHandlerContext source;
        private static final AbstractRecycler<PooledChannelHandlerContext> RECYCLER = new AbstractRecycler<PooledChannelHandlerContext>() {
            @Override
            protected PooledChannelHandlerContext newInstance() {
                return new PooledChannelHandlerContext();
            }
        };

        private PooledChannelHandlerContext() {}

        public static PooledChannelHandlerContext newInstance(ChannelHandlerContext source) {
            PooledChannelHandlerContext instance = RECYCLER.getInstance();
            instance.wrap(source);
            return instance;
        }

        public <T> boolean hasAttr(AttributeKey<T> attributeKey) {
            Channel channel = source.channel();
            Method method = ReflectUtil.getAccessibleMethod(channel.getClass(), "hasAttr",AttributeKey.class);
            try {
                return (boolean) method.invoke(channel,attributeKey);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Channel channel() {
            return source.channel();
        }

        @Override
        public EventExecutor executor() {
            return source.executor();
        }

        @Override
        public String name() {
            return source.name();
        }

        @Override
        public ChannelHandler handler() {
            return source.handler();
        }

        @Override
        public boolean isRemoved() {
            return source.isRemoved();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            source.fireChannelRegistered();
            return this;
        }

        @Deprecated
        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            source.fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            source.fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            source.fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            source.fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object event) {
            source.fireUserEventTriggered(event);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            source.fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            source.fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            source.fireChannelWritabilityChanged();
            return this;
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            return source.bind(localAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            return source.connect(remoteAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return source.connect(remoteAddress, localAddress);
        }

        @Override
        public ChannelFuture disconnect() {
            return source.disconnect();
        }

        @Override
        public ChannelFuture close() {
            return source.close();
        }

        @Deprecated
        @Override
        public ChannelFuture deregister() {
            return source.deregister();
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            return source.bind(localAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            return source.connect(remoteAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            return source.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            return source.disconnect(promise);
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            return source.close(promise);
        }

        @Deprecated
        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            return source.deregister(promise);
        }

        @Override
        public ChannelHandlerContext read() {
            source.read();
            return this;
        }

        @Override
        public ChannelFuture write(Object msg) {
            return source.write(msg);
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return source.write(msg, promise);
        }

        @Override
        public ChannelHandlerContext flush() {
            source.flush();
            return this;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            return source.writeAndFlush(msg, promise);
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return source.writeAndFlush(msg);
        }

        @Override
        public ChannelPipeline pipeline() {
            return source.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return ForceDirectPoooledByteBufAllocator.INSTANCE;
        }

        @Override
        public ChannelPromise newPromise() {
            return source.newPromise();
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return source.newProgressivePromise();
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return source.newSucceededFuture();
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return source.newFailedFuture(cause);
        }

        @Override
        public ChannelPromise voidPromise() {
            return source.voidPromise();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return source.channel().attr(key);
        }

        @Override
        public void recycle() {
            this.source = null;
            RECYCLER.recycleInstance(this);
        }

        @Override
        public void wrap(ChannelHandlerContext source) {
            Objects.requireNonNull(source);
            this.source = source;
        }

        @Override
        public ChannelHandlerContext unwrap() {
            return source;
        }
    }

    private static final class ForceDirectPoooledByteBufAllocator implements ByteBufAllocator {
        static ByteBufAllocator INSTANCE = new ForceDirectPoooledByteBufAllocator();

        @Override
        public ByteBuf buffer() {
            return ByteBufAllocatorX.INSTANCE.directBuffer();
        }

        @Override
        public ByteBuf buffer(int initialCapacity) {
            return ByteBufAllocatorX.INSTANCE.directBuffer(initialCapacity);
        }

        @Override
        public ByteBuf buffer(int initialCapacity, int maxCapacity) {
            return ByteBufAllocatorX.INSTANCE.directBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public ByteBuf ioBuffer() {
            return ByteBufAllocatorX.INSTANCE.directBuffer();
        }

        @Override
        public ByteBuf ioBuffer(int initialCapacity) {
            return ByteBufAllocatorX.INSTANCE.directBuffer(initialCapacity);
        }

        @Override
        public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
            return ByteBufAllocatorX.INSTANCE.directBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public ByteBuf heapBuffer() {
            return ByteBufAllocatorX.INSTANCE.heapBuffer();
        }

        @Override
        public ByteBuf heapBuffer(int initialCapacity) {
            return ByteBufAllocatorX.INSTANCE.heapBuffer(initialCapacity);
        }

        @Override
        public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
            return ByteBufAllocatorX.INSTANCE.heapBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public ByteBuf directBuffer() {
            return ByteBufAllocatorX.INSTANCE.directBuffer();
        }

        @Override
        public ByteBuf directBuffer(int initialCapacity) {
            return ByteBufAllocatorX.INSTANCE.directBuffer(initialCapacity);
        }

        @Override
        public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
            return ByteBufAllocatorX.INSTANCE.directBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public CompositeByteBuf compositeBuffer() {
            return ByteBufAllocatorX.INSTANCE.compositeBuffer();
        }

        @Override
        public CompositeByteBuf compositeBuffer(int maxNumComponents) {
            return ByteBufAllocatorX.INSTANCE.compositeBuffer(maxNumComponents);
        }

        @Override
        public CompositeByteBuf compositeHeapBuffer() {
            return ByteBufAllocatorX.INSTANCE.compositeHeapBuffer();
        }

        @Override
        public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
            return ByteBufAllocatorX.INSTANCE.compositeHeapBuffer(maxNumComponents);
        }

        @Override
        public CompositeByteBuf compositeDirectBuffer() {
            return ByteBufAllocatorX.INSTANCE.compositeDirectBuffer();
        }

        @Override
        public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
            return ByteBufAllocatorX.INSTANCE.compositeDirectBuffer(maxNumComponents);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return ByteBufAllocatorX.INSTANCE.isDirectBufferPooled();
        }

        public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
            return ByteBufAllocatorX.INSTANCE.calculateNewCapacity(minNewCapacity, maxCapacity);
        }
    }
}
