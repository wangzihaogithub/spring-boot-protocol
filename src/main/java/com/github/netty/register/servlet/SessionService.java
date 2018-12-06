package com.github.netty.register.servlet;

import com.github.netty.annotation.RegisterFor;

import java.util.List;

/**
 * session会话服务
 * @author acer01
 * 2018/8/19/019
 */
@RegisterFor.RpcService(value = "/hrpc/sessionService",timeout = 1000)
public interface SessionService {

    /**
     * 获取session (根据id)
     * @param sessionId
     * @return
     */
    Session getSession(@RegisterFor.RpcParam("sessionId")String sessionId);

    /**
     * 保存session
     * @param session
     */
    void saveSession(@RegisterFor.RpcParam("session")Session session);

    /**
     * 删除session
     * @param sessionId
     */
    void removeSession(@RegisterFor.RpcParam("sessionId")String sessionId);

    /**
     * 删除session (批量)
     * @param sessionIdList
     */
    void removeSessionBatch(@RegisterFor.RpcParam("sessionIdList")List<String> sessionIdList);

    /**
     * 改变sessionId
     * @param oldSessionId
     * @param newSessionId
     */
    void changeSessionId(@RegisterFor.RpcParam("oldSessionId")String oldSessionId,@RegisterFor.RpcParam("newSessionId")String newSessionId);

    /**
     * 获取session数量
     * @return
     */
    int count();

}
