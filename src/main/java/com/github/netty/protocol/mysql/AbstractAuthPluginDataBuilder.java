/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.github.netty.protocol.mysql;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 *
 */
public abstract class AbstractAuthPluginDataBuilder<B extends AbstractAuthPluginDataBuilder> {
    public final ByteBuf authPluginData = Unpooled.buffer();
    public final Set<CapabilityFlags> capabilities = CapabilityFlags.getImplicitCapabilities();

    public B addCapabilities(CapabilityFlags... capabilities) {
        Collections.addAll(this.capabilities, capabilities);
        return (B) this;
    }

    public B addCapabilities(Collection<CapabilityFlags> capabilities) {
        this.capabilities.addAll(capabilities);
        return (B) this;
    }

    public boolean hasCapability(CapabilityFlags capability) {
        return capabilities.contains(capability);
    }

    public B addAuthData(byte[] bytes) {
        authPluginData.writeBytes(bytes);
        return (B) this;
    }

    public B addAuthData(ByteBuf buf, int length) {
        authPluginData.writeBytes(buf, length);
        return (B) this;
    }

}
