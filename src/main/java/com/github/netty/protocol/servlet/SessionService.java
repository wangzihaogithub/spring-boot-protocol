package com.github.netty.protocol.servlet;

import com.github.netty.annotation.Protocol;

import java.util.List;

/**
 * Session service
 * @author wangzihao
 * 2018/8/19/019
 */
@Protocol.RpcService(value = "/_nrpc/sessionService",timeout = 1000)
public interface SessionService {

    /**
     * Get session (by id)
     * @param sessionId sessionId
     * @return Session
     */
    Session getSession(@Protocol.RpcParam("sessionId") String sessionId);

    /**
     * Save the session
     * @param session session
     */
    void saveSession(@Protocol.RpcParam("session") Session session);

    /**
     * Delete session
     * @param sessionId sessionId
     */
    void removeSession(@Protocol.RpcParam("sessionId") String sessionId);

    /**
     * Delete session (batch)
     * @param sessionIdList sessionIdList
     */
    void removeSessionBatch(@Protocol.RpcParam("sessionIdList") List<String> sessionIdList);

    /**
     * Change the sessionId
     * @param oldSessionId oldSessionId
     * @param newSessionId newSessionId
     */
    void changeSessionId(@Protocol.RpcParam("oldSessionId") String oldSessionId, @Protocol.RpcParam("newSessionId") String newSessionId);

    /**
     * Get the number of sessions
     * @return count
     */
    int count();

}
