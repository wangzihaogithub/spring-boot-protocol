package com.github.netty.protocol.mysql.client;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.protocol.mysql.ColumnType;
import com.github.netty.protocol.mysql.MysqlPacket;
import com.github.netty.protocol.mysql.Session;
import com.github.netty.protocol.mysql.server.ServerColumnDefinitionPacket;
import com.github.netty.protocol.mysql.server.ServerEofPacket;
import com.github.netty.protocol.mysql.server.ServerResultsetRowPacket;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ChannelHandler.Sharable
public class MysqlClientBusinessHandler extends AbstractChannelHandler<ClientPacket,MysqlPacket> {
    private static Pattern SETTINGS_PATTERN = Pattern.compile("@@(\\w+)\\sAS\\s(\\w+)");
    private int maxPacketSize;
    private Session session;
    public MysqlClientBusinessHandler() {
        super(false);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, ClientPacket msg) throws Exception {
        if (msg instanceof ClientHandshakePacket) {
            ctx.pipeline().replace(ClientConnectionDecoder.class,
                    "ClientCommandDecoder", new ClientCommandDecoder(getMaxPacketSize()));
        }
        onMysqlPacket(ctx, msg);
    }

    protected void onMysqlPacket(ChannelHandlerContext ctx, ClientPacket packet){

    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    /**
     * String query = packet.getQuery();
     * if (isServerSettingQuery(query)) {
     *      sendSettingPacket(ctx, packet);
     * }
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

    private ServerColumnDefinitionPacket newColumnDefinition(int packetSequence, String name, String orgName, ColumnType columnType, int length) {
        return ServerColumnDefinitionPacket.builder()
                .sequenceId(packetSequence)
                .name(name)
                .orgName(orgName)
                .type(columnType)
                .columnLength(length)
                .build();
    }
}
