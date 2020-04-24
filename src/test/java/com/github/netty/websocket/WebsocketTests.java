package com.github.netty.websocket;

import com.github.netty.nrpc.NRpcTests;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端测试 ( Websocket服务端口 : 8080)
 * 从Http协议协商至Websocket STOMP协议
 *
 * @author wangzihao
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NRpcTests.class)
public class WebsocketTests {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketTests.class);
    private static final WebSocketStompClient client = new WebSocketStompClient(new SockJsClient(Arrays.asList(new WebSocketTransport(new StandardWebSocketClient()))));
    static {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();
        client.setTaskScheduler(taskScheduler);
        client.setMessageConverter(new MappingJackson2MessageConverter());
    }

    public static void main(String[] args) {
        ScheduledExecutorService scheduledService = Executors.newScheduledThreadPool(3);
        //发起连接的次数
        AtomicInteger connectCount = new AtomicInteger();
        //连接成功数
        AtomicInteger successCount = new AtomicInteger();
        //连接失败数
        AtomicInteger errorCount = new AtomicInteger();
        //链接的列表
        List<StompSession> sessionList = new CopyOnWriteArrayList<>();
        //订阅的列表
        List<StompSession.Subscription> subscriptionList = new CopyOnWriteArrayList<>();


        //连接并订阅
        String url = "ws://localhost:8080/my-websocket?access_token=b90b0e77-63cf-4b05-8d8b-43ebefc71a6a";
        Runnable connectRunnable = newConnectAndSubscribeRunnable(url, connectCount, successCount, errorCount, sessionList, subscriptionList);
        scheduledService.scheduleAtFixedRate(connectRunnable, 0, 1000, TimeUnit.MILLISECONDS);//1秒间隔 一次新连接

        //发送消息
        Runnable sendMessageRunnable = newSendMessageRunnable(sessionList);
        scheduledService.scheduleAtFixedRate(sendMessageRunnable, 0, 1000, TimeUnit.MILLISECONDS);//1秒间隔 所有会话发送消息
        scheduledService.scheduleAtFixedRate(() -> {
            //每次5 秒打印一次详情
            logger.info("  连接数：" + connectCount + "  成功数：" + successCount + "  失败数：" + errorCount);
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * 发送消息
     *
     * @param sessionList
     * @return
     */
    private static Runnable newSendMessageRunnable(List<StompSession> sessionList) {
        return new Runnable() {
            @Override
            public void run() {
                int i = 0;
                for (StompSession session : sessionList) {
                    i++;

                    StompHeaders headers = new StompHeaders();
                    headers.setDestination("/app/receiveMessage");
                    headers.set("my-login-user", "小" + i);

                    Map<String, Object> payload = new HashMap<>(2);
                    payload.put("msg", "你好");

                    session.send(headers, payload);
                }
            }
        };
    }

    /**
     * 连接 并且 订阅一个主题
     * @param url
     * @param connectCount
     * @param successCount
     * @param errorCount
     * @param connectList
     * @return
     */
    private static Runnable newConnectAndSubscribeRunnable(String url, AtomicInteger connectCount, AtomicInteger successCount, AtomicInteger errorCount,
                                                           List<StompSession> connectList, List<StompSession.Subscription> subscriptionList) {
        return new Runnable() {
            int i = 0;
            Random random = new Random();
            @Override
            public void run() {
                try {
                    WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
                    String accessToken = "user" + i;
                    httpHeaders.add("access_token", accessToken);
                    //连接至url
                    StompSession session = client.connect(url, httpHeaders, new StompSessionHandlerAdapter() {
                        @Override
                        public void afterConnected(StompSession session, StompHeaders headers) {
                            successCount.incrementAndGet();
                        }

                        @Override
                        public void handleTransportError(StompSession session, Throwable exception) {
                            errorCount.incrementAndGet();
                        }
                    }).get();
                    connectList.add(session);

                    //订阅一个主题
                    String destination = "/app/user/room/user" + random.nextInt(connectList.size()) + "/room" + random.nextInt(connectList.size());
                    StompSession.Subscription subscription = session.subscribe(destination, new StompSessionHandlerAdapter() {
                    });
                    subscriptionList.add(subscription);

                    i++;
                } catch (Exception e) {
                    //
                } finally {
                    connectCount.incrementAndGet();
                }
            }
        };
    }

}
