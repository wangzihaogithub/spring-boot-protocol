package com.github.netty.mqtt;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产者测试 (一直运行)  注: mqtt-broker端口=8080
 * <p>
 * 用于测试qps性能, 直接右键运行即可
 * MQTT协议
 * @author wangzihao
 * 2020/4/24
 */
public class MqttProducerBootstrap {
    private static LoggerX logger = LoggerFactoryX.getLogger(MqttProducerBootstrap.class);
    private static final AtomicInteger PUBLISH_COUNT = new AtomicInteger();

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

                client.connect(MqttBrokerBootstrap.PORT,"localhost", asyncResult -> {
                    Runnable publishTask = () -> {
                        Buffer buffer = Buffer.buffer("数据" + PUBLISH_COUNT.incrementAndGet());
                        client.publish("/hello",buffer,
                                MqttQoS.EXACTLY_ONCE, true, true,
                                asyncResult1 -> {
                                    if (asyncResult1.succeeded()) {
                                        logger.info("发布数据至topic=/hello成功 {}", asyncResult1);
                                    }
                                }
                        );
                    };
                    Executors.newScheduledThreadPool(1)
                            .scheduleAtFixedRate(publishTask, 0, 1000, TimeUnit.MILLISECONDS);
                });
            }
        };
        Vertx.vertx().deployVerticle(verticle);
    }

}
