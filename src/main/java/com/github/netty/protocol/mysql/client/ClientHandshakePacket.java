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

package com.github.netty.protocol.mysql.client;

import com.github.netty.protocol.mysql.AbstractAuthPluginDataBuilder;
import com.github.netty.protocol.mysql.CapabilityFlags;
import com.github.netty.protocol.mysql.Constants;
import com.github.netty.protocol.mysql.MysqlCharacterSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handshake packet respons
 */
public class ClientHandshakePacket extends DefaultByteBufHolder implements ClientPacket {
    private final long timestamp = System.currentTimeMillis();
    private final Set<CapabilityFlags> capabilities = EnumSet.noneOf(CapabilityFlags.class);
    private final int sequenceId;
    private final int maxPacketSize;
    private final MysqlCharacterSet characterSet;
    private final String username;
    private final String database;
    private final String authPluginName;
    private final Map<String, String> attributes = new LinkedHashMap<String, String>();

    private ClientHandshakePacket(Builder builder) {
        super(builder.authPluginData);
        this.capabilities.addAll(builder.capabilities);
        this.sequenceId = builder.sequenceId;
        this.maxPacketSize = builder.maxPacketSize;
        this.characterSet = builder.characterSet;
        this.username = builder.username;
        this.database = builder.database;
        this.authPluginName = builder.authPluginName;
        this.attributes.putAll(builder.attributes);
    }

    public static Builder create() {
        return new Builder();
    }

    public static ClientHandshakePacket createSslResponse(Set<CapabilityFlags> capabilities, int maxPacketSize,
                                                          MysqlCharacterSet characterSet) {
        return create()
                .maxPacketSize(maxPacketSize)
                .characterSet(characterSet)
                .addCapabilities(capabilities)
                .addCapabilities(CapabilityFlags.CLIENT_SSL)
                .build();
    }

    public ByteBuf getAuthPluginData() {
        return content();
    }

    public Set<CapabilityFlags> getCapabilities() {
        return EnumSet.copyOf(capabilities);
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public MysqlCharacterSet getCharacterSet() {
        return characterSet;
    }

    public String getUsername() {
        return username;
    }

    public String getDatabase() {
        return database;
    }

    public String getAuthPluginName() {
        return authPluginName;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public int getSequenceId() {
        return sequenceId;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "," + database + "," + username + "," + attributes;
    }

    public static class Builder extends AbstractAuthPluginDataBuilder<Builder> {
        private int maxPacketSize = Constants.DEFAULT_MAX_PACKET_SIZE;
        private int sequenceId;
        private MysqlCharacterSet characterSet = MysqlCharacterSet.DEFAULT;
        private String username;
        private String database;
        private String authPluginName;
        private Map<String, String> attributes = new LinkedHashMap<>();

        public Builder sequenceId(int sequenceId) {
            this.sequenceId = sequenceId;
            return this;
        }

        public Builder maxPacketSize(int maxPacketSize) {
            this.maxPacketSize = maxPacketSize;
            return this;
        }

        public Builder characterSet(MysqlCharacterSet characterSet) {
            if (characterSet != null) {
                this.characterSet = characterSet;
            }
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder database(String database) {
            addCapabilities(CapabilityFlags.CLIENT_CONNECT_WITH_DB);
            this.database = database;
            return this;
        }

        public Builder authPluginName(String authPluginName) {
            addCapabilities(CapabilityFlags.CLIENT_PLUGIN_AUTH);
            this.authPluginName = authPluginName;
            return this;
        }

        public Builder addAttribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        public ClientHandshakePacket build() {
            return new ClientHandshakePacket(this);
        }
    }
}
