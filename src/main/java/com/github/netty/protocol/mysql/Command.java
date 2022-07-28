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
 * see com.mysql.cj.protocol.a.NativeConstants
 */
public enum Command {
    COM_SLEEP(0),
    COM_QUIT(1),
    COM_INIT_DB(2),
    COM_QUERY(3),
    COM_FIELD_LIST(4),//Not used; deprecated in MySQL 5.7.11 and MySQL 8.0.0.
    COM_CREATE_DB(5),//Not used; deprecated?
    COM_DROP_DB(6),//Not used; deprecated?
    COM_REFRESH(7),//Not used; deprecated in MySQL 5.7.11 and MySQL 8.0.0.
    COM_SHUTDOWN(8),//Deprecated in MySQL 5.7.9 and MySQL 8.0.0.
    COM_STATISTICS(9),
    COM_PROCESS_INFO(10),//Not used; deprecated in MySQL 5.7.11 and MySQL 8.0.0.
    COM_CONNECT(11),
    COM_PROCESS_KILL(12),//Not used; deprecated in MySQL 5.7.11 and MySQL 8.0.0.
    COM_DEBUG(13),
    COM_PING(14),
    COM_TIME(15),
    COM_DELAYED_INSERT(16),
    COM_CHANGE_USER(17),
    COM_BINLOG_DUMP(18),
    COM_TABLE_DUMP(19),
    COM_CONNECT_OUT(20),
    COM_REGISTER_SLAVE(21),

    /*Prepared statements*/
    COM_STMT_PREPARE(22),
    COM_STMT_EXECUTE(23),
    COM_STMT_SEND_LONG_DATA(24),
    COM_STMT_CLOSE(25),
    COM_STMT_RESET(26),

    /*Stored procedures*/
    COM_SET_OPTION(27),
    COM_STMT_FETCH(28),


    COM_DAEMON(29),
    COM_BINLOG_DUMP_GTID(30),
    COM_RESET_CONNECTION(31);

    private final int commandCode;

    Command(int commandCode) {
        this.commandCode = commandCode;
    }

    public static Command findByCommandCode(int code) {
        for (Command command : values()) {
            if (command.getCommandCode() == code) {
                return command;
            }
        }
        return null;
    }

    public int getCommandCode() {
        return commandCode;
    }
}
