package com.github.netty.protocol.servlet;

import com.github.netty.annotation.Protocol;

import java.util.List;

/**
 * session会话服务
 * @author acer01
 * 2018/8/19/019
 */
@Protocol.RpcService(value = "/hrpc/sessionService",timeout = 1000)
public interface SessionService {

    /**
     * 获取session (根据id)
     * @param sessionId
     * @return
     */
    Session getSession(@Protocol.RpcParam("sessionId")String sessionId);

    /**
     * 保存session
     * @param session
     */
    void saveSession(@Protocol.RpcParam("session")Session session);

    /**
     * 删除session
     * @param sessionId
     */
    void removeSession(@Protocol.RpcParam("sessionId")String sessionId);

    /**
     * 删除session (批量)
     * @param sessionIdList
     */
    void removeSessionBatch(@Protocol.RpcParam("sessionIdList")List<String> sessionIdList);

    /**
     * 改变sessionId
     * @param oldSessionId
     * @param newSessionId
     */
    void changeSessionId(@Protocol.RpcParam("oldSessionId")String oldSessionId, @Protocol.RpcParam("newSessionId")String newSessionId);

    /**
     * 获取session数量
     * @return
     */
    int count();

}
