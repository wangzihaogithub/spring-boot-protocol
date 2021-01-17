/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package com.github.netty.core.util;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Packet monitoring (read write/byte)
 * @author wangzihao
 */
@ChannelHandler.Sharable
public class BytesMetricsChannelHandler extends AbstractChannelHandler<ByteBuf,ByteBuf> {
    private static final AttributeKey<BytesMetrics> ATTR_KEY_METRICS = AttributeKey.valueOf(BytesMetrics.class+"#BytesMetrics");
    private AtomicLong readBytes = new AtomicLong();
    private AtomicLong writeBytes = new AtomicLong();

    public BytesMetricsChannelHandler() {
        super(false);
        Runtime.getRuntime().addShutdownHook(new Thread("Metrics-Hook" + hashCode()){
            @Override
            public void run() {
                logger.info("Metrics bytes[read={}/byte, write={}/byte]", readBytes, writeBytes);
            }
        });
    }

    @Override
    public void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        BytesMetrics metrics = getOrSetMetrics(ctx.channel());
        metrics.incrementRead(msg.readableBytes());
        ctx.fireChannelRead(msg);
    }

    @Override
    protected void onMessageWriter(ChannelHandlerContext ctx, ByteBuf msg, ChannelPromise promise) throws Exception {
        BytesMetrics metrics = getOrSetMetrics(ctx.channel());
        metrics.incrementWrote(msg.writableBytes());
        if(promise.isVoid()) {
            ctx.write(msg, promise);
        }else {
            ctx.write(msg, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        BytesMetrics metrics = getOrSetMetrics(ctx.channel());
        readBytes.getAndAdd(metrics.bytesRead());
        writeBytes.getAndAdd(metrics.bytesWrote());
        ctx.close(promise);
    }

    public static BytesMetrics getOrSetMetrics(Channel channel) {
        Attribute<BytesMetrics> attribute = channel.attr(ATTR_KEY_METRICS);
        BytesMetrics metrics = attribute.get();
        if(metrics == null) {
            metrics = new BytesMetrics();
            attribute.set(metrics);
        }
        return metrics;
    }

}
