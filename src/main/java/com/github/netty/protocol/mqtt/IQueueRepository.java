package com.github.netty.protocol.mqtt;

import java.util.Queue;

public interface IQueueRepository {

    Queue<MqttSessionRegistry.EnqueuedMessage> createQueue(String clientId, boolean clean);
}
