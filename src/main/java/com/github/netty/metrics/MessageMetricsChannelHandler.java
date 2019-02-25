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
import io.netty.channel.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Communication monitoring (read write/time)
 * @author wangzihao
 */
@ChannelHandler.Sharable
public class MessageMetricsChannelHandler extends AbstractChannelHandler<Object,Object> {
    private static final AttributeKey<MessageMetrics> ATTR_KEY_METRICS = AttributeKey.valueOf(MessageMetrics.class+"#MessageMetrics");
    private AtomicLong readMessages = new AtomicLong();
    private AtomicLong writeMessages = new AtomicLong();

    public MessageMetricsChannelHandler() {
        super(false);
        Runtime.getRuntime().addShutdownHook(new Thread("Metrics-Hook" + hashCode()){
            @Override
            public void run() {
                logger.info("Metrics messages[read={}/count, write={}/count]", readMessages, writeMessages);
            }
        });
    }

    @Override
    public void onMessageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        MessageMetrics metrics = getOrSetMetrics(ctx.channel());
        metrics.incrementRead(1);
        ctx.fireChannelRead(msg);
    }

    @Override
    protected void onMessageWriter(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        MessageMetrics metrics = getOrSetMetrics(ctx.channel());
        metrics.incrementWrote(1);
        if(promise.isVoid()) {
            ctx.write(msg, promise);
        }else {
            ctx.write(msg, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        MessageMetrics metrics = getOrSetMetrics(ctx.channel());
        readMessages.getAndAdd(metrics.messagesRead());
        writeMessages.getAndAdd(metrics.messagesWrote());
        ctx.close(promise);
    }

    public static MessageMetrics getOrSetMetrics(Channel channel) {
        Attribute<MessageMetrics> attribute = channel.attr(ATTR_KEY_METRICS);
        MessageMetrics metrics = attribute.get();
        if(metrics == null) {
            metrics = new MessageMetrics();
            attribute.set(metrics);
        }
        return metrics;
    }

}
