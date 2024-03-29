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

/**
 * Field flags.
 * <p>
 * http://dev.mysql.com/doc/refman/5.7/en/c-api-data-structures.html
 */
public enum ColumnFlag {
    NOT_NULL,
    PRI_KEY,
    UNIQUE_KEY,
    MULTIPLE_KEY,
    UNSIGNED,
    ZEROFILL,
    BINARY,
    AUTO_INCREMENT,
    ENUM,
    SET,
    BLOB,
    TIMESTAMP,
    NUM,
    NO_DEFAULT_VALUE,
    UNKNOWN14,
    UNKNOWN15
}