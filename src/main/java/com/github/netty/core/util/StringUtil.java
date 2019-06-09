package com.github.netty.core.util;

/**
 * Created by wangzihao on 2018/7/31/031.
 */
public class StringUtil {

    public static boolean isNotEmpty(CharSequence str){
        return !isEmpty(str);
    }

    public static boolean isEmpty(Object str) {
        return str == null || "".equals(str);
    }

    public static String firstUpperCase(String str){
        if(str == null || str.isEmpty() || Character.isUpperCase(str.charAt(0))){
            return str;
        }

        char[] cs= str.toCharArray();
        cs[0] -= 32;
        return new String(cs);
    }

    public static String firstLowerCase(String str){
        if(str == null || str.isEmpty() || Character.isLowerCase(str.charAt(0))){
            return str;
        }

        char[] cs= str.toCharArray();
        cs[0] =  Character.toLowerCase(cs[0]);
        return new String(cs);
    }

    public static String simpleClassName(String className) {
        if(className == null){
            return null;
        }
        final int lastDotIdx = className.lastIndexOf('.');
        if (lastDotIdx > -1) {
            return className.substring(lastDotIdx + 1);
        }
        return className;
    }

}
