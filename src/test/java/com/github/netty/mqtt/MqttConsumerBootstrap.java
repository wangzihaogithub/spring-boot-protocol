package com.github.netty.mqtt;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消费者测试 (一直运行)  注: mqtt服务端口=8080
 * <p>
 * 用于测试qps性能, 直接右键运行即可
 * MQTT协议
 * @author wangzihao
 * 2020/4/24
 */
public class MqttConsumerBootstrap {
    private static LoggerX logger = LoggerFactoryX.getLogger(MqttConsumerBootstrap.class);
    private static final int PORT = 8080;
    private static final String HOST = "localhost";

    public static void main(String[] args) {
        Verticle verticle = new AbstractVerticle() {
            @Override
            public void start() {
                MqttClient client = MqttClient.create(vertx, new MqttClientOptions()
                        //开启遗言
                        .setWillFlag(true)
                        .setWillTopic("willTopic")
                        .setWillMessage("hello")

                        .setUsername("admin")
                        .setPassword("123456")
                        .setMaxMessageSize(8192));

                client.connect(PORT,HOST,s -> {
                    client.publishHandler(response -> {
                        String message = new String(response.payload().getBytes());
                        logger.info("接收到消息: {} from topic {}", message, response.topicName());
                    });

                    client.subscribe("#", MqttQoS.AT_LEAST_ONCE.value(), resp -> {
                        logger.info("subscribe {}", resp);
                    });
                });
            }
        };
        Vertx.vertx().deployVerticle(verticle);
    }

}
