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

import com.github.netty.protocol.mysql.AbstractMySqlPacket;
import com.github.netty.protocol.mysql.ServerStatusFlag;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 *
 */
public class ServerOkPacket extends AbstractMySqlPacket implements ServerPacket {

    private final long affectedRows;
    private final long lastInsertId;

    private final int warnings;
    private final String info;

    private final Set<ServerStatusFlag> statusFlags = EnumSet.noneOf(ServerStatusFlag.class);
    private final String sessionStateChanges;


    public ServerOkPacket(Builder builder) {
        super(builder.sequenceId);
        affectedRows = builder.affectedRows;
        lastInsertId = builder.lastInsertId;

        warnings = builder.warnings;
        info = builder.info;

        statusFlags.addAll(builder.statusFlags);
        sessionStateChanges = builder.sessionStateChanges;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getAffectedRows() {
        return affectedRows;
    }

    public long getLastInsertId() {
        return lastInsertId;
    }

    public int getWarnings() {
        return warnings;
    }

    public String getInfo() {
        return info;
    }

    public Set<ServerStatusFlag> getStatusFlags() {
        return EnumSet.copyOf(statusFlags);
    }

    public String getSessionStateChanges() {
        return sessionStateChanges;
    }

    @Override
    public String toString() {
        return super.toString() + "," + statusFlags;
    }

    public static class Builder {
        private int sequenceId;

        private long affectedRows;
        private long lastInsertId;

        private int warnings;
        private String info;

        private Set<ServerStatusFlag> statusFlags = EnumSet.noneOf(ServerStatusFlag.class);
        private String sessionStateChanges;

        public Builder sequenceId(int sequenceId) {
            this.sequenceId = sequenceId;
            return this;
        }

        public Builder affectedRows(long affectedRows) {
            this.affectedRows = affectedRows;
            return this;
        }

        public Builder lastInsertId(long lastInsertId) {
            this.lastInsertId = lastInsertId;
            return this;
        }

        public Builder addStatusFlags(ServerStatusFlag statusFlag, ServerStatusFlag... statusFlags) {
            this.statusFlags.add(statusFlag);
            Collections.addAll(this.statusFlags, statusFlags);
            return this;
        }

        public Builder addStatusFlags(Collection<ServerStatusFlag> statusFlags) {
            this.statusFlags.addAll(statusFlags);
            return this;
        }

        public Builder warnings(int warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder info(String info) {
            this.info = info;
            return this;
        }

        public Builder sessionStateChanges(String sessionStateChanges) {
            this.sessionStateChanges = sessionStateChanges;
            return this;
        }

        public ServerOkPacket build() {
            return new ServerOkPacket(this);
        }
    }
}
