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
package com.github.netty.protocol.mqtt.config;

public class BrokerConfiguration {

    private final boolean allowAnonymous;
    private final boolean allowZeroByteClientId;
    private final boolean reauthorizeSubscriptionsOnConnect;

    public BrokerConfiguration() {
        this(true, false, false);
    }

    public BrokerConfiguration(boolean allowAnonymous, boolean allowZeroByteClientId,
                               boolean reauthorizeSubscriptionsOnConnect) {
        this.allowAnonymous = allowAnonymous;
        this.allowZeroByteClientId = allowZeroByteClientId;
        this.reauthorizeSubscriptionsOnConnect = reauthorizeSubscriptionsOnConnect;
    }

    public boolean isAllowAnonymous() {
        return allowAnonymous;
    }

    public boolean isAllowZeroByteClientId() {
        return allowZeroByteClientId;
    }

    public boolean isReauthorizeSubscriptionsOnConnect() {
        return reauthorizeSubscriptionsOnConnect;
    }
}
