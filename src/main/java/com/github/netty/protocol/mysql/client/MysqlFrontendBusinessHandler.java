package com.github.netty.protocol.mysql.client;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.protocol.mysql.*;
import com.github.netty.protocol.mysql.listener.MysqlPacketListener;
import com.github.netty.protocol.mysql.server.*;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Here the user business logic
 * <p>
 * follows
 * 1. server to client {@link ServerHandshakePacket}
 * 2. client to server {@link ClientHandshakePacket}
 * 3. server to client {@link ServerOkPacket}
 * 4. client to server query... {@link ClientQueryPacket}
 * 5. server to client {@link ServerOkPacket}
 * 6. any....
 * <p>
 * Initial Handshake starts with server sending the `Initial Handshake Packet` {@link ServerHandshakePacket}.
 * After this, optionally,
 * client can request an SSL connection to be established with `SSL Connection Request Packet` TODO ,
 * and then client sends the `Handshake Response Packet` {@link ClientHandshakePacket}.
 */
public class MysqlFrontendBusinessHandler extends AbstractChannelHandler<ClientPacket, MysqlPacket> {
    protected static Pattern SETTINGS_PATTERN = Pattern.compile("@@(\\w+)\\sAS\\s(\\w+)");
    private int maxPacketSize;
    private Session session;
    private Collection<MysqlPacketListener> mysqlPacketListeners;

    public MysqlFrontendBusinessHandler() {
        super(false);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ClientPacket msg) throws Exception {
        if (msg instanceof ClientHandshakePacket) {
            onHandshake(ctx, (ClientHandshakePacket) msg);
        }
        if (mysqlPacketListeners != null && !mysqlPacketListeners.isEmpty()) {
            for (MysqlPacketListener mysqlPacketListener : mysqlPacketListeners) {
                try {
                    mysqlPacketListener.onMysqlPacket(msg, ctx, session, Constants.HANDLER_TYPE_FRONTEND);
                } catch (Exception e) {
                    logger.warn("{} exception = {} ", mysqlPacketListener.toString(), e.toString(), e);
                }
            }
        }
        onMysqlPacket(ctx, msg);
    }

    protected void onMysqlPacket(ChannelHandlerContext ctx, ClientPacket packet) {

    }

    @Override
    protected void onUserEventTriggered(ChannelHandlerContext ctx, Object evt) {
        super.onUserEventTriggered(ctx, evt);
        if (evt instanceof EventHandshakeSuccessful) {
            onHandshakeSuccessful(ctx, (EventHandshakeSuccessful) evt);
        }
    }

    protected void onHandshake(ChannelHandlerContext ctx, ClientHandshakePacket packet) {
        session.setClientCharsetAttr(packet.getCharacterSet());
        session.setFrontendCapabilities(packet.getCapabilities());
    }

    protected void onHandshakeSuccessful(ChannelHandlerContext ctx, EventHandshakeSuccessful event) {
        if (ctx.pipeline().context(ClientConnectionDecoder.class) != null) {
            ctx.pipeline().replace(ClientConnectionDecoder.class,
                    "ClientCommandDecoder", new ClientCommandDecoder(session, getMaxPacketSize()));
        }
    }

    public ClientHandshakePacket newClientHandshakePacket(String user, String password, String database,
                                                          ServerHandshakePacket serverHandshakePacket,
                                                          Set<CapabilityFlags> capabilities) {
        ClientHandshakePacket packet = ClientHandshakePacket.create()
                .addCapabilities(capabilities)
                .username(user)
                .addAuthData(MysqlNativePasswordUtil.hashPassword(password, serverHandshakePacket.getAuthPluginData()))
                .database(database)
                .authPluginName(Constants.MYSQL_NATIVE_PASSWORD)
                .build();
        return packet;
    }

    /**
     * String query = packet.getQuery();
     * if (isServerSettingQuery(query)) {
     * sendSettingPacket(ctx, packet);
     * }
     *
     * @param query query sql
     * @return isServerSettingQuery
     */
    protected boolean isServerSettingQuery(String query) {
        query = query.toLowerCase();
        return query.contains("select") && !query.contains("from") && query.contains("@@");
    }

    protected ChannelFuture writeAndFlushSettingPacket(ChannelHandlerContext ctx, ClientQueryPacket query) {
        Matcher matcher = SETTINGS_PATTERN.matcher(query.getQuery());
        List<String> values = new ArrayList<>();
        int sequenceId = query.getSequenceId();
        while (matcher.find()) {
            String systemVariable = matcher.group(1);
            String fieldName = matcher.group(2);
            switch (systemVariable) {
                case "character_set_client":
                case "character_set_connection":
                case "character_set_results":
                case "character_set_server":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 12));
                    values.add("utf8");
                    break;
                case "collation_server":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 21));
                    values.add("utf8_general_ci");
                    break;
                case "init_connect":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 0));
                    values.add("");
                    break;
                case "interactive_timeout":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 21));
                    values.add("28800");
                    break;
                case "language":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 0));
                    values.add("");
                    break;
                case "license":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 21));
                    values.add("ASLv2");
                    break;
                case "lower_case_table_names":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 63));
                    values.add("2");
                    break;
                case "max_allowed_packet":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 63));
                    values.add("4194304");
                    break;
                case "net_buffer_length":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 63));
                    values.add("16384");
                    break;
                case "net_write_timeout":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 63));
                    values.add("60");
                    break;
                case "have_query_cache":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 6));
                    values.add("YES");
                    break;
                case "sql_mode":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 0));
                    values.add("");
                    break;
                case "system_time_zone":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 6));
                    values.add("UTC");
                    break;
                case "time_zone":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 12));
                    values.add("SYSTEM");
                    break;
                case "tx_isolation":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 12));
                    values.add("REPEATABLE-READ");
                    break;
                case "wait_timeout":
                    ctx.write(newColumnDefinition(sequenceId++, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONGLONG, 12));
                    values.add("28800");
                    break;
                default: {
//                    throw new Error("Unknown system variable " + systemVariable);
                }
            }
        }
        ctx.write(new ServerEofPacket(++sequenceId, 0));
        ctx.write(new ServerResultsetRowPacket(++sequenceId, values.toArray(new String[0])));
        return ctx.writeAndFlush(new ServerEofPacket(++sequenceId, 0));
    }

    protected ServerColumnDefinitionPacket newColumnDefinition(int packetSequence, String name, String orgName, ColumnType columnType, int length) {
        return ServerColumnDefinitionPacket.builder()
                .sequenceId(packetSequence)
                .name(name)
                .orgName(orgName)
                .type(columnType)
                .columnLength(length)
                .build();
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Collection<MysqlPacketListener> getMysqlPacketListeners() {
        return mysqlPacketListeners;
    }

    public void setMysqlPacketListeners(Collection<MysqlPacketListener> mysqlPacketListeners) {
        this.mysqlPacketListeners = mysqlPacketListeners;
    }
}
