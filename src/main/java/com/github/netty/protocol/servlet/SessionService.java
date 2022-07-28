package com.github.netty.protocol.servlet;

import com.github.netty.annotation.NRpcParam;
import com.github.netty.annotation.NRpcService;

import java.util.List;

/**
 * Session service
 *
 * @author wangzihao
 * 2018/8/19/019
 */
@NRpcService(value = "/_nrpc/sessionService", timeout = 1000)
public interface SessionService {

    /**
     * Get session (by id)
     *
     * @param sessionId sessionId
     * @return Session
     */
    Session getSession(@NRpcParam("sessionId") String sessionId);

    /**
     * Save the session
     *
     * @param session session
     */
    void saveSession(@NRpcParam("session") Session session);

    /**
     * Delete session
     *
     * @param sessionId sessionId
     */
    void removeSession(@NRpcParam("sessionId") String sessionId);

    /**
     * Delete session (batch)
     *
     * @param sessionIdList sessionIdList
     */
    void removeSessionBatch(@NRpcParam("sessionIdList") List<String> sessionIdList);

    /**
     * Change the sessionId
     *
     * @param oldSessionId oldSessionId
     * @param newSessionId newSessionId
     */
    void changeSessionId(@NRpcParam("oldSessionId") String oldSessionId, @NRpcParam("newSessionId") String newSessionId);

    /**
     * Get the number of sessions
     *
     * @return count
     */
    int count();

}
