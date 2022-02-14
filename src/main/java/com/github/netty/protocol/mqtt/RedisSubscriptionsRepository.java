///*
// * Copyright (c) 2012-2018 The original author or authors
// * ------------------------------------------------------
// * All rights reserved. This program and the accompanying materials
// * are made available under the terms of the Eclipse Public License v1.0
// * and Apache License v2.0 which accompanies this distribution.
// *
// * The Eclipse Public License is available at
// * http://www.eclipse.org/legal/epl-v10.html
// *
// * The Apache License v2.0 is available at
// * http://www.opensource.org/licenses/apache2.0.php
// *
// * You may elect to redistribute this code under either of these licenses.
// */
//package com.github.netty.protocol.mqtt;
//
//import com.github.netty.core.AbstractChannelHandler;
//import com.github.netty.core.AbstractNettyClient;
//import com.github.netty.protocol.mqtt.subscriptions.Subscription;
//import io.netty.channel.*;
//import io.netty.handler.codec.redis.*;
//
//import java.net.InetSocketAddress;
//import java.nio.charset.StandardCharsets;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
///**
// * @author wangzihao
// */
//public class RedisSubscriptionsRepository extends AbstractNettyClient implements ISubscriptionsRepository {
//
//    private final List<Subscription> subscriptions = new ArrayList<>();
//
//    public RedisSubscriptionsRepository(String remoteHost, int remotePort) {
//        super("Redis",new InetSocketAddress(remoteHost, remotePort));
//    }
//
//    @Override
//    public List<Subscription> listAllSubscriptions() {
//        return Collections.unmodifiableList(subscriptions);
//    }
//
//    @Override
//    public void addNewSubscription(Subscription subscription) {
//        subscriptions.add(subscription);
//    }
//
//    @Override
//    public void removeSubscription(String topic, String clientID) {
//        subscriptions.stream()
//            .filter(s -> s.getTopicFilter().toString().equals(topic) && s.getClientId().equals(clientID))
//            .findFirst()
//            .ifPresent(subscriptions::remove);
//    }
//
//    @Override
//    protected ChannelInitializer<? extends Channel> newBossChannelHandler() {
//        return new ChannelInitializer<Channel>() {
//            @Override
//            protected void initChannel(Channel ch) throws Exception {
//                ChannelPipeline pipeline = ch.pipeline();
//                pipeline.addLast(new RedisDecoder());
//                pipeline.addLast(new RedisBulkStringAggregator());
//                pipeline.addLast(new RedisArrayAggregator());
//                pipeline.addLast(new RedisEncoder());
//            }
//        };
//    }
//
//    class RedisChannelHandler extends AbstractChannelHandler<RedisMessage,RedisMessage>{
//        @Override
//        protected void onMessageReceived(ChannelHandlerContext ctx, RedisMessage msg) throws Exception {
//            read(msg);
//        }
//
//        private void read(RedisMessage msg){
//            if (msg instanceof SimpleStringRedisMessage) {
//                System.out.println(((SimpleStringRedisMessage) msg).content());
//            } else if (msg instanceof ErrorRedisMessage) {
//                System.out.println(((ErrorRedisMessage) msg).content());
//            } else if (msg instanceof IntegerRedisMessage) {
//                System.out.println(((IntegerRedisMessage) msg).value());
//            } else if (msg instanceof FullBulkStringRedisMessage) {
//                System.out.println(getString((FullBulkStringRedisMessage) msg));
//            } else if (msg instanceof ArrayRedisMessage) {
//                for (RedisMessage child : ((ArrayRedisMessage) msg).children()) {
//                    read(child);
//                }
//            }
//        }
//
//        private String getString(FullBulkStringRedisMessage msg) {
//            if (msg.isNull()) {
//                return "(null)";
//            }
//            return msg.content().toString(StandardCharsets.UTF_8);
//        }
//        @Override
//        protected void onMessageWriter(ChannelHandlerContext ctx, RedisMessage msg, ChannelPromise promise) throws Exception {
////            String[] commands = ((String) msg).split("\\s+");
////            List<RedisMessage> children = new ArrayList<>(commands.length);
////            for (String cmdString : commands) {
////                children.add(new FullBulkStringRedisMessage(ByteBufUtil.writeUtf8(ctx.alloc(), cmdString)));
////            }
////            RedisMessage request = new ArrayRedisMessage(children);
////            ctx.write(request, promise);
//        }
//    }
//}
