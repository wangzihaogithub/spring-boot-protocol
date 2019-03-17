package com.github.netty.core;

import com.github.netty.core.util.SystemPropertyUtil;

/**
 * Created by wangzihao on 2018/9/9/009.
 */
public class CoreConstants {
    private static int rpcLockSpinCount;
    private static int recyclerCount;

    static {
        rpcLockSpinCount = SystemPropertyUtil.getInt("netty-core.rpcLockSpinCount",0);
        recyclerCount = SystemPropertyUtil.getInt("netty-core.recyclerCount",30);
    }

    /**
     * The number of RPC lock spins. If no response can be obtained after N times, the block will occur
     * @return rpcLockSpinCount
     */
    public static int getRpcLockSpinCount(){
        return rpcLockSpinCount;
    }

    /**
     * The recycling number
     * @return recyclerCount
     */
    public static int getRecyclerCount() {
        return recyclerCount;
    }

}
