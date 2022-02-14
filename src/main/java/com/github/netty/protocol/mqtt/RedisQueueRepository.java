//package com.github.netty.protocol.mqtt;
//
//import java.util.Queue;
//import java.util.concurrent.ConcurrentLinkedQueue;
//
//public class RedisQueueRepository implements IQueueRepository {
//
//    @Override
//    public Queue<MqttSessionRegistry.EnqueuedMessage> createQueue(String cli, boolean clean) {
//        return new ConcurrentLinkedQueue<>();
//    }
//}
