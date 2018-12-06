package com.github.netty.core.constants;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.SystemPropertyUtil;

import java.util.function.Supplier;

/**
 * Created by acer01 on 2018/9/9/009.
 */
public class CoreConstants {

    private static boolean isEnableExecuteHold;
    private static boolean isEnableRawNetty;
    private static int rpcLockSpinCount;
    //开启埋点的超时打印
    public static boolean isEnableExecuteHold(){
        return isEnableExecuteHold;
    }
    //开启日志
    public static boolean isEnableLog() {
        return true;
    }
    //开启原生netty, 不走spring的dispatchServlet
    public static boolean isEnableRawNetty() {
        return isEnableRawNetty;
    }
    //rpc锁自旋次数, 如果N次后还拿不到响应,则堵塞
    public static int getRpcLockSpinCount(){
        return rpcLockSpinCount;
    }

    static {
        isEnableExecuteHold = SystemPropertyUtil.getBoolean("netty-rpc.isEnableExecuteHold",false);
        isEnableRawNetty = SystemPropertyUtil.getBoolean("netty-rpc.isEnableRawNetty",false);
        rpcLockSpinCount = SystemPropertyUtil.getInt("netty-rpc.rpcLockSpinCount",150);
    }


    private static LoggerX logger = LoggerFactoryX.getLogger(CoreConstants.class);

    public static void holdExecute(Runnable runnable){
        long c= System.currentTimeMillis();
        try {
            runnable.run();
        }
        catch (Throwable throwable){
            logger.error(throwable.toString() +" - "+ new Throwable().getStackTrace()[1]);
//            throw throwable;
        }finally {
            long end = System.currentTimeMillis() - c;
            if(end > 5){
                logger.info(" 耗时["+end+"]"+new Throwable().getStackTrace()[1]);
            }
        }
    }

    public static <T>T holdExecute(Supplier<T> runnable){
        long c= System.currentTimeMillis();
        try {
            return runnable.get();
        }catch (Throwable throwable){
            logger.error(throwable.toString() +" - "+ new Throwable().getStackTrace()[1]);
//            throw throwable;
            return null;
        } finally {
            long end = System.currentTimeMillis() - c;
            if(end > 5){
                logger.info(" 耗时["+end+"]"+new Throwable().getStackTrace()[1]);
            }
        }
    }
}
