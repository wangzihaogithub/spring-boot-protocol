package com.github.netty.core.constants;

import com.github.netty.core.util.SystemPropertyUtil;

/**
 * 版本适配, 用于兼容netty4 升级netty5
 *
 * @author acer01
 * 2018/8/5/005
 */
public class VersionConstants {

    /**
     *是否开启版本适配, 默认不开启
     * 开启影响性能,有些netty4的版本还是不适配, 不太成熟
     */
    private static boolean isEnableVersionAdapter;

    static {
        isEnableVersionAdapter = SystemPropertyUtil.getBoolean("netty-rpc.isEnableVersionAdapter",false);;
    }

    public static boolean isEnableVersionAdapter() {
        return isEnableVersionAdapter;
    }

}
