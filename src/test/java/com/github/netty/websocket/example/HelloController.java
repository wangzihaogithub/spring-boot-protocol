package com.github.netty.websocket.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 我的房间Controller (供websocket客户端订阅或请求)
 *
 * @author wangzihao
 */
@RestController
public class HelloController {
    private Logger logger = LoggerFactory.getLogger(getClass());
    //所有在线的用户
    @Autowired
    private SimpUserRegistry userRegistry;
    //发送消息的工具类 (可以主动回复客户端)
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 订阅房间
     *
     * @param message   消息
     * @param principal 订阅人的用户身份（当前登录人的信息）
     * @param username  被订阅人的账号
     * @param roomName  被订阅的房间名
     */
    @SubscribeMapping("/user/room/{username}/{roomName}")
    public void subscribeMyRoom(Message message, Principal principal, @DestinationVariable("username") String username, @DestinationVariable("roomName") String roomName) {
        logger.info("[" + principal.getName() + "]订阅了[" + username + "]的 [" + roomName + "] 房间");
    }

    /**
     * 接收消息
     *
     * @param message   客户端的数据
     * @param principal 当前登录人的信息
     */
    @MessageMapping("/receiveMessage")
    public void receiveMessage(Message message, Principal principal) {
        String payload = new String((byte[]) message.getPayload());
        logger.info("已收到[" + principal.getName() + "]的消息[" + payload + "] 当前在线人数[" + userRegistry.getUserCount() + "]");
    }

}
