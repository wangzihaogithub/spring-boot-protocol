package com.github.netty.core;

/**
 * Server listening
 * Created by wangzihao on 2018/11/12/012.
 */
public interface ServerListener {

    void onServerStart() throws Exception;
    void onServerStop() throws Exception;
    /**
     * Priority order
     * @return The smaller the value of order, the more likely it is to be executed first
     */
    int order();
}
