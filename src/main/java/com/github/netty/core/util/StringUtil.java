package com.github.netty.core.util;

/**
 * Created by acer01 on 2018/7/31/031.
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

    public static String capitalize(String str) {
        return changeFirstCharacterCase(str, true);
    }

    private static String changeFirstCharacterCase(String str, boolean capitalize) {
        if(str == null || str.isEmpty()) {
            return str;
        } else {
            char baseChar = str.charAt(0);
            char updatedChar;
            if(capitalize) {
                updatedChar = Character.toUpperCase(baseChar);
            } else {
                updatedChar = Character.toLowerCase(baseChar);
            }

            if(baseChar == updatedChar) {
                return str;
            } else {
                char[] chars = str.toCharArray();
                chars[0] = updatedChar;
                return new String(chars, 0, chars.length);
            }
        }
    }

}
