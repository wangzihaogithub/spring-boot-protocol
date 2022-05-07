package com.github.netty.mqtt;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

/**
 * 消费者测试 (一直运行)  注: mqtt-broker端口=8080
 * <p>
 * 用于测试qps性能, 直接右键运行即可
 * MQTT协议
 * @author wangzihao
 * 2020/4/24
 */
public class MqttConsumerBootstrap {
    private static LoggerX logger = LoggerFactoryX.getLogger(MqttConsumerBootstrap.class);

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

                client.connect(MqttBrokerBootstrap.PORT,"localhost", s -> {
                    client.publishHandler(response -> {
                        String message = new String(response.payload().getBytes());
                        logger.info("订阅收到topic={}的数据: {}", response.topicName(),message);
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
