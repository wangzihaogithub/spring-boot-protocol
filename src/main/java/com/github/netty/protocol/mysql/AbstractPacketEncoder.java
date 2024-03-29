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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 *
 */
public abstract class AbstractPacketEncoder<T extends MysqlPacket> extends MessageToByteEncoder<T> {

    @Override
    final protected void encode(ChannelHandlerContext ctx, T packet, ByteBuf buf) throws Exception {
        int writerIdx = buf.writerIndex();
        buf.writeInt(0); // Advance the writer index so we can set the packet length after encoding
        encodePacket(ctx, packet, buf);
        int len = buf.writerIndex() - writerIdx - 4;
        buf.setMediumLE(writerIdx, len)
                .setByte(writerIdx + 3, packet.getSequenceId());
    }

    protected abstract void encodePacket(ChannelHandlerContext ctx, T packet, ByteBuf buf) throws Exception;

}
