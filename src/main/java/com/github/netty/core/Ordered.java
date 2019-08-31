package com.github.netty.core;

/**
 * Ordered
 * @author wangzihao
 *  2019/8/31/022
 */
public interface Ordered {
    /**
     * Priority order
     * @return The smaller the value of order, the more likely it is to be executed first
     */
    int getOrder();

}
