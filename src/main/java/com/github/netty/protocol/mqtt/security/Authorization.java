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

package com.github.netty.protocol.mqtt.security;

import com.github.netty.protocol.mqtt.subscriptions.Topic;

import static com.github.netty.protocol.mqtt.security.Authorization.Permission.READWRITE;

/**
 * Carries the read/write authorization to topics for the users.
 */
public class Authorization {

    protected final Topic topic;
    protected final Permission permission;

    Authorization(Topic topic) {
        this(topic, Permission.READWRITE);
    }

    Authorization(Topic topic, Permission permission) {
        this.topic = topic;
        this.permission = permission;
    }

    public boolean grant(Permission desiredPermission) {
        return permission == desiredPermission || permission == READWRITE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Authorization that = (Authorization) o;

        if (permission != that.permission)
            return false;
        return topic.equals(that.topic);

    }

    @Override
    public int hashCode() {
        int result = topic.hashCode();
        result = 31 * result + permission.hashCode();
        return result;
    }

    /**
     * Access rights
     */
    enum Permission {
        READ, WRITE, READWRITE
    }
}
