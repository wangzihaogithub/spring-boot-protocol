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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects all the metrics from the various pipeline.
 */
public class MessageMetricsCollector {

    private AtomicLong readMessages = new AtomicLong();
    private AtomicLong wroteMessages = new AtomicLong();

    public MessageMetrics computeMetrics() {
        MessageMetrics allMetrics = new MessageMetrics();
        allMetrics.incrementRead(readMessages.get());
        allMetrics.incrementWrote(wroteMessages.get());
        return allMetrics;
    }

    public void sumReadMessages(long count) {
        readMessages.getAndAdd(count);
    }

    public void sumWroteMessages(long count) {
        wroteMessages.getAndAdd(count);
    }
}
