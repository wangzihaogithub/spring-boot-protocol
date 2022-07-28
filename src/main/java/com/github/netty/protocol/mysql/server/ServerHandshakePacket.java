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

package com.github.netty.protocol.mysql.server;

import com.github.netty.protocol.mysql.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.util.AsciiString;

import java.util.*;

/**
 *
 */
public class ServerHandshakePacket extends DefaultByteBufHolder implements ServerPacket {

    public static final int DEFAULT_PROTOCOL_VERSION = 10;
    private final long timestamp = System.currentTimeMillis();
    private final int sequenceId;
    private final int protocolVersion;
    private final AsciiString serverVersion;
    private final int connectionId;
    private final Set<CapabilityFlags> capabilities;
    private final MysqlCharacterSet characterSet;
    private final Set<ServerStatusFlag> serverStatus;
    private final String authPluginName;

    private ServerHandshakePacket(Builder builder) {
        super(builder.authPluginData);
        if (builder.authPluginData.readableBytes() < Constants.AUTH_PLUGIN_DATA_PART1_LEN) {
            throw new IllegalArgumentException("authPluginData can not contain less than " + Constants.AUTH_PLUGIN_DATA_PART1_LEN + " bytes.");
        }
        protocolVersion = builder.protocolVersion;
        if (builder.serverVersion == null) {
            throw new NullPointerException("serverVersion can not be null");
        }
        sequenceId = builder.sequenceId;
        serverVersion = AsciiString.of(builder.serverVersion);
        connectionId = builder.connectionId;
        capabilities = Collections.unmodifiableSet(builder.capabilities);
        characterSet = builder.characterSet;
        serverStatus = Collections.unmodifiableSet(builder.serverStatus);
        authPluginName = builder.authPluginName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public AsciiString getServerVersion() {
        return serverVersion;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public ByteBuf getAuthPluginData() {
        return content();
    }

    public Set<CapabilityFlags> getCapabilities() {
        return capabilities;
    }

    public MysqlCharacterSet getCharacterSet() {
        return characterSet;
    }

    public Set<ServerStatusFlag> getServerStatus() {
        return serverStatus;
    }

    public String getAuthPluginName() {
        return authPluginName;
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ServerHandshakePacket handshake = (ServerHandshakePacket) o;
        return protocolVersion == handshake.protocolVersion &&
                connectionId == handshake.connectionId &&
                Objects.equals(serverVersion, handshake.serverVersion) &&
                Objects.equals(capabilities, handshake.capabilities) &&
                characterSet == handshake.characterSet &&
                Objects.equals(serverStatus, handshake.serverStatus) &&
                Objects.equals(authPluginName, handshake.authPluginName);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "," + serverVersion + "," + serverStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), protocolVersion, serverVersion, connectionId, capabilities, characterSet, serverStatus, authPluginName);
    }

    public static class Builder extends AbstractAuthPluginDataBuilder<Builder> {
        private int protocolVersion = DEFAULT_PROTOCOL_VERSION;
        private CharSequence serverVersion;
        private int connectionId = -1;
        private int sequenceId;
        private MysqlCharacterSet characterSet = MysqlCharacterSet.DEFAULT;
        private Set<ServerStatusFlag> serverStatus = EnumSet.noneOf(ServerStatusFlag.class);
        private String authPluginName;

        public Builder sequenceId(int sequenceId) {
            this.sequenceId = sequenceId;
            return this;
        }

        public Builder protocolVersion(int protocolVerison) {
            this.protocolVersion = protocolVerison;
            return this;
        }

        public Builder serverVersion(CharSequence serverVersion) {
            this.serverVersion = serverVersion;
            return this;
        }

        public Builder connectionId(int connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        public Builder characterSet(MysqlCharacterSet characterSet) {
            this.characterSet = characterSet == null ? MysqlCharacterSet.DEFAULT : characterSet;
            return this;
        }

        public Builder addServerStatus(ServerStatusFlag serverStatus, ServerStatusFlag... serverStatuses) {
            this.serverStatus.add(serverStatus);
            Collections.addAll(this.serverStatus, serverStatuses);
            return this;
        }

        public Builder addServerStatus(Collection<ServerStatusFlag> serverStatus) {
            this.serverStatus.addAll(serverStatus);
            return this;
        }

        public Builder authPluginName(String authPluginName) {
            capabilities.add(CapabilityFlags.CLIENT_PLUGIN_AUTH);
            this.authPluginName = authPluginName;
            return this;
        }

        public ServerHandshakePacket build() {
            return new ServerHandshakePacket(this);
        }
    }
}
