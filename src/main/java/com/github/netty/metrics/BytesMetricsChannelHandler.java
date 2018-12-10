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

package com.github.netty.metrics;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

@ChannelHandler.Sharable
public class BytesMetricsChannelHandler extends AbstractChannelHandler<ByteBuf,ByteBuf> {
    private static final AttributeKey<BytesMetrics> ATTR_KEY_METRICS = AttributeKey.valueOf(BytesMetrics.class+"#BytesMetrics");
    private BytesMetricsCollector collector;

    public BytesMetricsChannelHandler(BytesMetricsCollector collector) {
        super(false);
        this.collector = collector;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Attribute<BytesMetrics> attr = ctx.channel().attr(ATTR_KEY_METRICS);
        attr.set(new BytesMetrics());

        super.channelActive(ctx);
    }

    @Override
    public void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        BytesMetrics metrics = getOrSetMetrics(ctx.channel());;
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
        collector.sumReadBytes(metrics.readBytes());
        collector.sumWroteBytes(metrics.wroteBytes());
        super.close(ctx, promise);
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
