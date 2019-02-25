package com.github.netty.core.util;

import java.lang.management.ManagementFactory;
import java.net.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.StringJoiner;

/**
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * Common functions that involve the host platform
 * @author wangzihao
 */
public class HostUtil {

    private static InetAddress inetAddress;
    private static String userName;
    private static String osName;
    private static String osArch;
    private static boolean embedded;
    private static boolean is64bit = false;
    private static int pid;

    static {
        embedded = AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> {
            osName = System.getProperty("os.name").toLowerCase();
            osArch = System.getProperty("os.arch").toLowerCase();

            String name = ManagementFactory.getRuntimeMXBean().getName();
            pid = Integer.parseInt(name.split("@")[0]);
            userName = name.split("@")[1];

            is64bit =  osArch.equals("x64")
                    || osArch.equals("x86_64")
                    || osArch.equals("ia64");

            return Boolean.getBoolean("com.sun.javafx.isEmbedded");
        });
    }

    /**
     * 端口是否已存在
     * @param port port
     * @return true = 存在, false=不存在
     */
    public static boolean isExistPort(int port) {
        try {
            Socket socket = new Socket();
            InetSocketAddress address = new InetSocketAddress("127.0.0.1",port);
            socket.bind(address);
            socket.close();
            return false;
        } catch (Exception e) {
            if(e instanceof BindException){
                return true;
            }
            return false;
        }
    }

    public static boolean isLocalhost(String host){
        return "localhost".equalsIgnoreCase(host) || host.contains("0.0.0.0") ||  host.contains("127.0.0.1");
    }

    public static int getPid(){
        return pid;
    }

    public static String getUserName(){
        return userName;
    }

    public static String getOsName() {
        return osName;
    }

    public static boolean is64Bit() {
        return is64bit;
    }

    public static boolean isWindows() {
        return osName.startsWith("windows");
    }

    public static boolean isMacOSX() {
        return osName.startsWith("mac os x");
    }

    public static boolean isLinux() {
        return osName.startsWith("linux");
    }

    public static boolean isIOS() {
        return osName.startsWith("ios");
    }

    public static String getIPAddress() {
        InetAddress inetAddress = getInetAddress();
        if(inetAddress == null){
            return null;
        }
        return inetAddress.getHostAddress();
    }

    public static InetAddress getInetAddress() {
        if(inetAddress != null){
            return inetAddress;
        }
        try {
            InetAddress address = null;
            // 遍历所有的网络接口
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                // 在所有的接口下再遍历IP
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
                    InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {// 排除loopback类型地址
                        if (inetAddr.isSiteLocalAddress()) {
                            // 如果是site-local地址，就是它了
                            inetAddress = inetAddr;
                            return inetAddress;
                        } else if (address == null) {
                            // site-local类型的地址未被发现，先记录候选地址
                            address = inetAddr;
                        }
                    }
                }
            }

            if (address == null) {
                // 如果没有发现 non-loopback地址.只能用最次选的方案
                address = InetAddress.getLocalHost();
            }
            inetAddress = address;
            return inetAddress;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if the platform is embedded.
     * @return 是否虚拟容器
     */
    public static boolean isEmbedded() {
        return embedded;
    }

    /**
     * mac地址
     * @return 例( ‎00-24-7E-0A-22-93)
     */
    public static String getMac() {
        try {
            Enumeration<NetworkInterface> el = NetworkInterface
                    .getNetworkInterfaces();
            while (el.hasMoreElements()) {
                byte[] mac = el.nextElement().getHardwareAddress();
                if (mac == null || mac.length <= 0) {
                    continue;
                }
                String hexstr = bytesToHexString(mac);
                return getSplitString(hexstr, "-", 2).toUpperCase();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return null;
    }

    private static String getSplitString(String str, String split, int length) {
        if(str == null || split.length() == 0){
            return "";
        }
        int len = str.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i % length == 0 && i > 0) {
                sb.append(split);
            }
            sb.append(str.charAt(i));
        }

        String[] attrs = sb.toString().split(split);
        StringJoiner stringJoiner = new StringJoiner(split);
        for (String attr : attrs) {
            stringJoiner.add(attr);
        }
        return stringJoiner.toString();
    }

    private static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");

        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

}
