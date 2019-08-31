package com.github.netty.core;

/**
 * Server listening
 * Created by wangzihao on 2018/11/12/012.
 */
public interface ServerListener extends Ordered{

    void onServerStart() throws Exception;
    void onServerStop() throws Exception;

    /**
     * default Priority order 0
     * @return The smaller the value of order, the more likely it is to be executed first
     */
    @Override
    default int getOrder(){
        return 0;
    }
}
