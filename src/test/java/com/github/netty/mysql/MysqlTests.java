package com.github.netty.mysql;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(classes = MysqlTests.class)
public class MysqlTests {
    private final MysqlDataSource dataSource = new MysqlDataSource();

    private static List<Map<String, Object>> dumpResultSet(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        while (resultSet.next()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                map.put(metaData.getColumnName(i), resultSet.getObject(i));
            }
            list.add(map);
        }
        return list;
    }

    public void init() {
        dataSource.setURL("jdbc:mysql://localhost:8080/ig_zues");
        dataSource.setUser("root");
        dataSource.setPassword("root");
    }

    @Test
    public void test() throws SQLException {
        init();

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from information_schema.schemata");
            List<Map<String, Object>> list = dumpResultSet(statement.executeQuery());

            Assert.isTrue(list.size() > 0);
        }
    }
}
