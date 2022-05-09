package com.github.netty.protocol.mqtt;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MemoryQueueRepository implements IQueueRepository {

    @Override
    public Queue<MqttSessionRegistry.EnqueuedMessage> createQueue(String clientId, boolean clean) {
        return new ConcurrentLinkedQueue<>();
    }
}
