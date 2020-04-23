package com.github.netty.mqtt;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.sql.SQLException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = MqttTests.class)
public class MqttTests {
    @Before
    public void init(){
        MqttBrokerBootstrap.main(new String[]{});

    }

    @Test
    public void test() throws Exception {
        // TODO: 4-23 023
    }

}
