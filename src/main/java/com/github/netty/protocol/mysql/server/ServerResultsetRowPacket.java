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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class ServerResultsetRowPacket extends AbstractMySqlPacket implements ServerPacket {
    private final List<String> values;

    public ServerResultsetRowPacket(int sequenceId, String... values) {
        super(sequenceId);
        this.values = new ArrayList<>(values.length);
        Collections.addAll(this.values, values);
    }

    public ServerResultsetRowPacket(int sequenceId, Collection<String> values) {
        super(sequenceId);
        this.values = new ArrayList<>(values.size());
        this.values.addAll(values);
    }

    public List<String> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return super.toString() + "," + values.size();
    }
}
