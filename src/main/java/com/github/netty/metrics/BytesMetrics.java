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

public class BytesMetrics {

    private long m_bytesRead;
    private long m_bytesWrote;

    void incrementRead(long numBytes) {
        m_bytesRead += numBytes;
    }

    void incrementWrote(long numBytes) {
        m_bytesWrote += numBytes;
    }

    public long bytesRead() {
        return m_bytesRead;
    }

    public long bytesWrote() {
        return m_bytesWrote;
    }
}
