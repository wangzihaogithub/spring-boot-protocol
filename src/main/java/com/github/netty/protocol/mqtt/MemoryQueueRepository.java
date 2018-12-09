package com.github.netty.protocol.mqtt;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MemoryQueueRepository implements IQueueRepository {

    @Override
    public Queue<SessionRegistry.EnqueuedMessage> createQueue(String cli, boolean clean) {
        return new ConcurrentLinkedQueue<>();
    }
}
