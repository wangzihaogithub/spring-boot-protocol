package com.github.netty.core.util;

import java.io.InputStream;
import java.util.Properties;

public class ServerInfo {
    private static final String SERVER_INFO;
    private static final String SERVER_BUILT;
    private static final String SERVER_NUMBER;
    private static final String JVM_VERSION;
    private static final String ARCH;
    private static final String OS_NAME;

    static {
        String info = null;
        String built = null;
        String number = null;

        Properties props = new Properties();
        try (InputStream is = ServerInfo.class.getResourceAsStream
                ("/server.properties")) {
            props.load(is);
            info = props.getProperty("server.info");
            built = props.getProperty("server.built");
            number = props.getProperty("server.number");
        } catch (Throwable t) {
            //
        }
        if (info == null) {
            info = "Github NettyX/2.0.x-dev";
        }
        if (built == null) {
            built = "unknown";
        }
        if (number == null) {
            number = "2.0.x";
        }
        SERVER_INFO = info;
        SERVER_BUILT = built;
        SERVER_NUMBER = number;
        OS_NAME = System.getProperty("os.name");
        ARCH = System.getProperty("os.arch");
        JVM_VERSION = System.getProperty("java.runtime.version");
    }

    public static String getServerInfo() {
        return SERVER_INFO;
    }

    public static String getServerBuilt() {
        return SERVER_BUILT;
    }

    public static String getServerNumber() {
        return SERVER_NUMBER;
    }

    public static String getJvmVersion() {
        return JVM_VERSION;
    }

    public static String getArch() {
        return ARCH;
    }

    public static String getOsName() {
        return OS_NAME;
    }

    public static void main(String args[]) {
        System.out.println("Server version: " + getServerInfo());
        System.out.println("Server built:   " + getServerBuilt());
        System.out.println("Server number:  " + getServerNumber());
        System.out.println("OS Name:        " +
                getOsName());
        System.out.println("OS Version:     " +
                System.getProperty("os.version"));
        System.out.println("Architecture:   " +
                getArch());
        System.out.println("JVM Version:    " +
                getJvmVersion());
        System.out.println("JVM Vendor:     " +
                System.getProperty("java.vm.vendor"));
    }
}
