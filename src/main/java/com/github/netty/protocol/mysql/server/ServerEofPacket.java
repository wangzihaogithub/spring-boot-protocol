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
public class ServerEofPacket extends AbstractMySqlPacket implements ServerPacket {

    private final int warnings;
    private final Set<ServerStatusFlag> statusFlags = EnumSet.noneOf(ServerStatusFlag.class);

    public ServerEofPacket(int sequenceId, int warnings, ServerStatusFlag... flags) {
        super(sequenceId);
        this.warnings = warnings;
        Collections.addAll(statusFlags, flags);
    }

    public ServerEofPacket(int sequenceId, int warnings, Collection<ServerStatusFlag> flags) {
        super(sequenceId);
        this.warnings = warnings;
        statusFlags.addAll(flags);
    }

    public int getWarnings() {
        return warnings;
    }

    public Set<ServerStatusFlag> getStatusFlags() {
        return statusFlags;
    }

    @Override
    public String toString() {
        return super.toString() + "," + statusFlags;
    }
}
