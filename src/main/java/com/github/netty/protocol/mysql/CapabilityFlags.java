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

import java.util.EnumSet;

/**
 * An enum of all the MySQL client/server capability flags.
 *
 * @see <a href="https://dev.mysql.com/doc/internals/en/capability-flags.html#packet-Protocol::CapabilityFlags">
 * Capability Flags Reference Documentation</a>
 */
public enum CapabilityFlags {
    CLIENT_LONG_PASSWORD,
    CLIENT_FOUND_ROWS,
    CLIENT_LONG_FLAG,
    CLIENT_CONNECT_WITH_DB,
    CLIENT_NO_SCHEMA,
    CLIENT_COMPRESS,
    CLIENT_ODBC,
    CLIENT_LOCAL_FILES,
    CLIENT_IGNORE_SPACE,
    CLIENT_PROTOCOL_41,
    CLIENT_INTERACTIVE,
    CLIENT_SSL,
    CLIENT_IGNORE_SIGPIPE,
    CLIENT_TRANSACTIONS,
    CLIENT_RESERVED,
    CLIENT_SECURE_CONNECTION,
    CLIENT_MULTI_STATEMENTS,
    CLIENT_MULTI_RESULTS,
    CLIENT_PS_MULTI_RESULTS,
    CLIENT_PLUGIN_AUTH,
    CLIENT_CONNECT_ATTRS,
    CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA,
    CLIENT_CAN_HANDLE_EXPIRED_PASSWORDS,
    CLIENT_SESSION_TRACK,
    CLIENT_DEPRECATE_EOF,
    UNKNOWN_25,
    UNKNOWN_26,
    UNKNOWN_27,
    UNKNOWN_28,
    UNKNOWN_29,
    UNKNOWN_30,
    UNKNOWN_31;

    public static EnumSet<CapabilityFlags> getImplicitCapabilities() {
        return EnumSet.of(
                CapabilityFlags.CLIENT_LONG_PASSWORD,
                CapabilityFlags.CLIENT_PROTOCOL_41,
                CapabilityFlags.CLIENT_TRANSACTIONS,
                CapabilityFlags.CLIENT_SECURE_CONNECTION
        );
    }

}
