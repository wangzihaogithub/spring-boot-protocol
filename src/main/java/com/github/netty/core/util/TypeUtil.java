/*
 * Copyright 1999-2017 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.netty.core.util;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class TypeUtil {

    public static boolean   compatibleWithJavaBean      = false;
    
    /** Output the input data according to the case of the field name */
    public static boolean   compatibleWithFieldName      = false;
    
    private static boolean  oracleTimestampMethodInited = false;
    private static Method   oracleTimestampMethod;

    private static boolean  oracleDateMethodInited      = false;
    private static Method   oracleDateMethod;

    static {
        try {
            compatibleWithJavaBean = true;
            compatibleWithFieldName = true;
        } catch (Throwable e) {
            // skip
        }
    }

    /**
     * 寻找声明的泛型
     * @param object 实例对象
     * @param parametrizedSuperclass 声明泛型的类
     * @param typeParamName 泛型名称
     * @return 泛型的实际类型
     */
    public static Class<?> findGenericType(final Object object, Class<?> parametrizedSuperclass, String typeParamName) {
        final Class<?> thisClass = object.getClass();
        Class<?> currentClass = thisClass;
        for (;;) {
            if (currentClass.getSuperclass() == parametrizedSuperclass) {
                int typeParamIndex = -1;
                TypeVariable<?>[] typeParams = currentClass.getSuperclass().getTypeParameters();
                for (int i = 0; i < typeParams.length; i ++) {
                    if (typeParamName.equals(typeParams[i].getName())) {
                        typeParamIndex = i;
                        break;
                    }
                }
                if (typeParamIndex < 0) {
                    throw new IllegalStateException(
                            "unknown type parameter '" + typeParamName + "': " + parametrizedSuperclass);
                }
                Type genericSuperType = currentClass.getGenericSuperclass();
                if (!(genericSuperType instanceof ParameterizedType)) {
                    return Object.class;
                }
                Type[] actualTypeParams = ((ParameterizedType) genericSuperType).getActualTypeArguments();
                Type actualTypeParam = actualTypeParams[typeParamIndex];
                if (actualTypeParam instanceof ParameterizedType) {
                    actualTypeParam = ((ParameterizedType) actualTypeParam).getRawType();
                }
                if (actualTypeParam instanceof Class) {
                    return (Class<?>) actualTypeParam;
                }
                if (actualTypeParam instanceof GenericArrayType) {
                    Type componentType = ((GenericArrayType) actualTypeParam).getGenericComponentType();
                    if (componentType instanceof ParameterizedType) {
                        componentType = ((ParameterizedType) componentType).getRawType();
                    }
                    if (componentType instanceof Class) {
                        return Array.newInstance((Class<?>) componentType, 0).getClass();
                    }
                }
                if (actualTypeParam instanceof TypeVariable) {
                    // Resolved type parameter points to another type parameter.
                    TypeVariable<?> v = (TypeVariable<?>) actualTypeParam;
                    currentClass = thisClass;
                    if (!(v.getGenericDeclaration() instanceof Class)) {
                        return Object.class;
                    }

                    parametrizedSuperclass = (Class<?>) v.getGenericDeclaration();
                    typeParamName = v.getName();
                    if (parametrizedSuperclass.isAssignableFrom(thisClass)) {
                        continue;
                    } else {
                        return Object.class;
                    }
                }
                return fail(thisClass, typeParamName);
            }
            currentClass = currentClass.getSuperclass();
            if (currentClass == null) {
                return fail(thisClass, typeParamName);
            }
        }
    }

    private static Class<?> fail(Class<?> type, String typeParamName) {
        throw new IllegalStateException(
                "cannot determine the type of the type parameter '" + typeParamName + "': " + type);
    }

    public static boolean isPrimitive(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return true;
        }
        if(clazz.equals(Boolean.class) ||
                clazz.equals(Byte.class) ||
                clazz.equals(Character.class) ||
                clazz.equals(Double.class) ||
                clazz.equals(Float.class) ||
                clazz.equals(Integer.class) ||
                clazz.equals(Long.class) ||
                clazz.equals(Short.class)) {
            return true;
        }
        return false;
    }

    public static <T> TypeResult getGenericType(Class<T> type,
                                                 Class<? extends T> clazz) {

        // Look to see if this class implements the interface of interest

        // Get all the interfaces
        Type[] interfaces = clazz.getGenericInterfaces();
        for (Type iface : interfaces) {
            // Only need to check interfaces that use generics
            if (iface instanceof ParameterizedType) {
                ParameterizedType pi = (ParameterizedType) iface;
                // Look for the interface of interest
                if (pi.getRawType() instanceof Class) {
                    if (type.isAssignableFrom((Class<?>) pi.getRawType())) {
                        return getTypeParameter(
                                clazz, pi.getActualTypeArguments()[0]);
                    }
                }
            }
        }

        // Interface not found on this class. Look at the superclass.
        @SuppressWarnings("unchecked")
        Class<? extends T> superClazz =
                (Class<? extends T>) clazz.getSuperclass();
        if (superClazz == null) {
            // Finished looking up the class hierarchy without finding anything
            return null;
        }

        TypeResult superClassTypeResult = getGenericType(type, superClazz);
        int dimension = superClassTypeResult.getDimension();
        if (superClassTypeResult.getIndex() == -1 && dimension == 0) {
            // Superclass implements interface and defines explicit type for
            // the interface of interest
            return superClassTypeResult;
        }

        if (superClassTypeResult.getIndex() > -1) {
            // Superclass implements interface and defines unknown type for
            // the interface of interest
            // Map that unknown type to the generic types defined in this class
            ParameterizedType superClassType =
                    (ParameterizedType) clazz.getGenericSuperclass();
            TypeResult result = getTypeParameter(clazz,
                    superClassType.getActualTypeArguments()[
                            superClassTypeResult.getIndex()]);
            result.incrementDimension(superClassTypeResult.getDimension());
            if (result.getClazz() != null && result.getDimension() > 0) {
                superClassTypeResult = result;
            } else {
                return result;
            }
        }

        if (superClassTypeResult.getDimension() > 0) {
            StringBuilder className = new StringBuilder();
            for (int i = 0; i < dimension; i++) {
                className.append('[');
            }
            className.append('L');
            className.append(superClassTypeResult.getClazz().getCanonicalName());
            className.append(';');

            Class<?> arrayClazz;
            try {
                arrayClazz = Class.forName(className.toString());
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }

            return new TypeResult(arrayClazz, -1, 0);
        }

        // Error will be logged further up the call stack
        return null;
    }

    /*
       * For a generic parameter, return either the Class used or if the type
       * is unknown, the index for the type in definition of the class
       */
    private static TypeResult getTypeParameter(Class<?> clazz, Type argType) {
        if (argType instanceof Class<?>) {
            return new TypeResult((Class<?>) argType, -1, 0);
        } else if (argType instanceof ParameterizedType) {
            return new TypeResult((Class<?>)((ParameterizedType) argType).getRawType(), -1, 0);
        } else if (argType instanceof GenericArrayType) {
            Type arrayElementType = ((GenericArrayType) argType).getGenericComponentType();
            TypeResult result = getTypeParameter(clazz, arrayElementType);
            if(result != null) {
                result.incrementDimension(1);
            }
            return result;
        } else {
            TypeVariable<?>[] tvs = clazz.getTypeParameters();
            for (int i = 0; i < tvs.length; i++) {
                if (tvs[i].equals(argType)) {
                    return new TypeResult(null, i, 0);
                }
            }
            return null;
        }
    }

    public static String castToString(Object value) {
        if (value == null) {
            return null;
        }

        return value.toString();
    }

    public static Byte castToByte(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }

        if (value instanceof String) {
            String strVal = (String) value;
            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }

            return Byte.parseByte(strVal);
        }

        throw new RuntimeException("can not cast to byte, value : " + value);
    }

    public static Character castToChar(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Character) {
            return (Character) value;
        }

        if (value instanceof String) {
            String strVal = (String) value;

            if (strVal.length() == 0) {
                return null;
            }

            if (strVal.length() != 1) {
                throw new IllegalArgumentException("can not cast to char, value : " + value);
            }

            return strVal.charAt(0);
        }

        throw new IllegalArgumentException("can not cast to char, value : " + value);
    }

    public static Short castToShort(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }

        if (value instanceof String) {
            String strVal = (String) value;

            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }

            return Short.parseShort(strVal);
        }

        throw new IllegalArgumentException("can not cast to short, value : " + value);
    }

    public static BigDecimal castToBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }

        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }

        String strVal = value.toString();
        if (strVal.length() == 0) {
            return null;
        }

        if (value instanceof Map && ((Map) value).size() == 0) {
            return null;
        }

        return new BigDecimal(strVal);
    }

    public static BigInteger castToBigInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }

        if (value instanceof Float || value instanceof Double) {
            return BigInteger.valueOf(((Number) value).longValue());
        }

        String strVal = value.toString();
        if (strVal.length() == 0 //
            || "null".equals(strVal) //
            || "NULL".equals(strVal)) {
            return null;
        }

        return new BigInteger(strVal);
    }

    public static Float castToFloat(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }

        if (value instanceof String) {
            String strVal = value.toString();
            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }
            
            if (strVal.indexOf(',') != 0) {
                strVal = strVal.replaceAll(",", "");
            }

            return Float.parseFloat(strVal);
        }

        throw new IllegalArgumentException("can not cast to float, value : " + value);
    }

    public static Double castToDouble(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        if (value instanceof String) {
            String strVal = value.toString();
            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }

            if (strVal.indexOf(',') != 0) {
                strVal = strVal.replaceAll(",", "");
            }

            return Double.parseDouble(strVal);
        }

        throw new IllegalArgumentException("can not cast to double, value : " + value);
    }

    public static Date castToDate(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Date) { // 使用频率最高的，应优先处理
            return (Date) value;
        }

        if (value instanceof Calendar) {
            return ((Calendar) value).getTime();
        }

        long longValue = -1;

        if (value instanceof Number) {
            longValue = ((Number) value).longValue();
            return new Date(longValue);
        }

        if (value instanceof String) {
            String strVal = (String) value;
            
//            JSONScanner dateLexer = new JSONScanner(strVal);
//            try {
//                if (dateLexer.scanISO8601DateIfMatch(false)) {
//                    Calendar calendar = dateLexer.getCalendar();
//                    return calendar.getTime();
//                }
//            } finally {
//                dateLexer.close();
//            }
            
            if (strVal.startsWith("/Date(") && strVal.endsWith(")/")) {
                String dotnetDateStr = strVal.substring(6, strVal.length() - 2);
                strVal = dotnetDateStr;
            }

            if (strVal.indexOf('-') != -1) {
                String format;
                if (strVal.length() == "yyyy-MM-dd HH:mm:ss".length()) {
                    format = "yyyy-MM-dd HH:mm:ss";;
                } else if (strVal.length() == 10) {
                    format = "yyyy-MM-dd";
                } else if (strVal.length() == "yyyy-MM-dd HH:mm:ss".length()) {
                    format = "yyyy-MM-dd HH:mm:ss";
                } else {
                    format = "yyyy-MM-dd HH:mm:ss.SSS";
                }

                SimpleDateFormat dateFormat = new SimpleDateFormat(format);
                try {
                    return (Date) dateFormat.parse(strVal);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("can not cast to Date, value : " + strVal);
                }
            }

            if (strVal.length() == 0) {
                return null;
            }

            longValue = Long.parseLong(strVal);
        }

        if (longValue < 0) {
            Class<?> clazz = value.getClass();
            if ("oracle.sql.TIMESTAMP".equals(clazz.getName())) {
                if (oracleTimestampMethod == null && !oracleTimestampMethodInited) {
                    try {
                        oracleTimestampMethod = clazz.getMethod("toJdbc");
                    } catch (NoSuchMethodException e) {
                        // skip
                    } finally {
                        oracleTimestampMethodInited = true;
                    }
                }

                Object result;
                try {
                    result = oracleTimestampMethod.invoke(value);
                } catch (Exception e) {
                    throw new IllegalArgumentException("can not cast oracle.sql.TIMESTAMP to Date", e);
                }
                return (Date) result;
            }

            if ("oracle.sql.DATE".equals(clazz.getName())) {
                if (oracleDateMethod == null && !oracleDateMethodInited) {
                    try {
                        oracleDateMethod = clazz.getMethod("toJdbc");
                    } catch (NoSuchMethodException e) {
                        // skip
                    } finally {
                        oracleDateMethodInited = true;
                    }
                }

                Object result;
                try {
                    result = oracleDateMethod.invoke(value);
                } catch (Exception e) {
                    throw new IllegalArgumentException("can not cast oracle.sql.DATE to Date", e);
                }
                return (Date) result;
            }

            throw new IllegalArgumentException("can not cast to Date, value : " + value);
        }

        return new Date(longValue);
    }

    public static java.sql.Date castToSqlDate(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof java.sql.Date) {
            return (java.sql.Date) value;
        }

        if (value instanceof Date) {
            return new java.sql.Date(((Date) value).getTime());
        }

        if (value instanceof Calendar) {
            return new java.sql.Date(((Calendar) value).getTimeInMillis());
        }

        long longValue = 0;

        if (value instanceof Number) {
            longValue = ((Number) value).longValue();
        }

        if (value instanceof String) {
            String strVal = (String) value;
            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }

            if (isNumber(strVal)) {
                longValue = Long.parseLong(strVal);
            } else {
//                JSONScanner scanner = new JSONScanner(strVal);
//                if (scanner.scanISO8601DateIfMatch(false)) {
//                    longValue = scanner.getCalendar().getTime().getTime();
//                } else {
//                    throw new IllegalArgumentException("can not cast to Timestamp, value : " + strVal);
//                }
            }
        }

        if (longValue <= 0) {
            //  忽略 1970-01-01 之前的时间处理？
            throw new IllegalArgumentException("can not cast to Date, value : " + value);
        }

        return new java.sql.Date(longValue);
    }

    public static java.sql.Timestamp castToTimestamp(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Calendar) {
            return new java.sql.Timestamp(((Calendar) value).getTimeInMillis());
        }

        if (value instanceof java.sql.Timestamp) {
            return (java.sql.Timestamp) value;
        }

        if (value instanceof Date) {
            return new java.sql.Timestamp(((Date) value).getTime());
        }

        long longValue = 0;

        if (value instanceof Number) {
            longValue = ((Number) value).longValue();
        }

        if (value instanceof String) {
            String strVal = (String) value;
            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }

            if (isNumber(strVal)) {
                longValue = Long.parseLong(strVal);
            } else {
//                JSONScanner scanner = new JSONScanner(strVal);
//                if (scanner.scanISO8601DateIfMatch(false)) {
//                    longValue = scanner.getCalendar().getTime().getTime();
//                } else {
//                    throw new IllegalArgumentException("can not cast to Timestamp, value : " + strVal);
//                }
            }
        }

        if (longValue <= 0) {
            throw new IllegalArgumentException("can not cast to Timestamp, value : " + value);
        }

        return new java.sql.Timestamp(longValue);
    }

    public static boolean isNumber(String str) {
        for (int i = 0; i < str.length(); ++i) {
            char ch = str.charAt(i);
            if (ch == '+' || ch == '-') {
                if (i != 0) {
                    return false;
                } else {
                    continue;
                }
            } else if (ch < '0' || ch > '9') {
                return false;
            }
        }

        return true;
    }

    public static Long castToLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (value instanceof String) {
            String strVal = (String) value;
            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }
            
            if (strVal.indexOf(',') != 0) {
                strVal = strVal.replaceAll(",", "");
            }

            try {
                return Long.parseLong(strVal);
            } catch (NumberFormatException ex) {
                //
            }
        }

        if (value instanceof Map) {
            Map map = (Map) value;
            if (map.size() == 2
                    && map.containsKey("andIncrement")
                    && map.containsKey("andDecrement")) {
                Iterator iter = map.values().iterator();
                iter.next();
                Object value2 = iter.next();
                return castToLong(value2);
            }
        }

        return null;
    }

    public static int castToInt(Object value,int def) {
        Integer ret;
        try {
            ret = castToInt(value);
            if(ret != null){
                return ret;
            }
        }catch (Exception e){
            //
        }
        return def;
    }

    public static Integer castToInt(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Integer) {
            return (Integer) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        if (value instanceof String) {
            String strVal = (String) value;

            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }
            
            if (strVal.indexOf(',') != 0) {
                strVal = strVal.replaceAll(",", "");
            }

            try {
                return Integer.parseInt(strVal);
            }catch (Exception e){
                return null;
            }
        }

        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? 1 : 0;
        }

        if (value instanceof Map) {
            Map map = (Map) value;
            if (map.size() == 2
                    && map.containsKey("andIncrement")
                    && map.containsKey("andDecrement")) {
                Iterator iter = map.values().iterator();
                iter.next();
                Object value2 = iter.next();
                return castToInt(value2);
            }
        }

        throw new IllegalArgumentException("can not cast to int, value : " + value);
    }

    public static byte[] castToBytes(Object value) {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }

        if (value instanceof String) {
            return ((String) value).getBytes();
        }
        throw new IllegalArgumentException("can not cast to int, value : " + value);
    }

    public static Boolean castToBoolean(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }

        if (value instanceof String) {
            String strVal = (String) value;

            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }

            if ("true".equalsIgnoreCase(strVal) //
                || "1".equals(strVal)) {
                return Boolean.TRUE;
            }
            
            if ("false".equalsIgnoreCase(strVal) //
                || "0".equals(strVal)) {
                return Boolean.FALSE;
            }

            if ("Y".equalsIgnoreCase(strVal) //
                    || "T".equals(strVal)) {
                return Boolean.TRUE;
            }

            if ("F".equalsIgnoreCase(strVal) //
                    || "N".equals(strVal)) {
                return Boolean.FALSE;
            }
        }

        throw new IllegalArgumentException("can not cast to boolean, value : " + value);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T cast(Object obj, Class<T> clazz) {
        if (obj == null) {
            if (clazz == int.class) {
                return (T) Integer.valueOf(0);
            } else if (clazz == long.class) {
                return (T) Long.valueOf(0);
            } else if (clazz == short.class) {
                return (T) Short.valueOf((short) 0);
            } else if (clazz == byte.class) {
                return (T) Byte.valueOf((byte) 0);
            } else if (clazz == float.class) {
                return (T) Float.valueOf(0);
            } else if (clazz == double.class) {
                return (T) Double.valueOf(0);
            } else if (clazz == boolean.class) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        if (clazz == null) {
            throw new IllegalArgumentException("clazz is null");
        }

        if (clazz == obj.getClass()) {
            return (T) obj;
        }

//        if (obj instanceof Map) {
//            if (clazz == Map.class) {
//                return (T) obj;
//            }
//
//            Map map = (Map) obj;
//            if (clazz == Object.class && !map.containsKey(JSON.DEFAULT_TYPE_KEY)) {
//                return (T) obj;
//            }
//
//            return castToJavaBean((Map<String, Object>) obj, clazz, config);
//        }

        if (clazz.isArray()) {
            if (obj instanceof Collection) {

                Collection collection = (Collection) obj;
                int index = 0;
                Object array = Array.newInstance(clazz.getComponentType(), collection.size());
                for (Object item : collection) {
                    Object value = cast(item, clazz.getComponentType());
                    Array.set(array, index, value);
                    index++;
                }

                return (T) array;
            }

            if (clazz == byte[].class) {
                return (T) castToBytes(obj);
            }
        }

        if (clazz.isAssignableFrom(obj.getClass())) {
            return (T) obj;
        }

        if (clazz == boolean.class || clazz == Boolean.class) {
            return (T) castToBoolean(obj);
        }

        if (clazz == byte.class || clazz == Byte.class) {
            return (T) castToByte(obj);
        }

        if (clazz == char.class || clazz == Character.class) {
            return (T) castToChar(obj);
        }

        if (clazz == short.class || clazz == Short.class) {
            return (T) castToShort(obj);
        }

        if (clazz == int.class || clazz == Integer.class) {
            return (T) castToInt(obj);
        }

        if (clazz == long.class || clazz == Long.class) {
            return (T) castToLong(obj);
        }

        if (clazz == float.class || clazz == Float.class) {
            return (T) castToFloat(obj);
        }

        if (clazz == double.class || clazz == Double.class) {
            return (T) castToDouble(obj);
        }

        if (clazz == String.class) {
            return (T) castToString(obj);
        }

        if (clazz == BigDecimal.class) {
            return (T) castToBigDecimal(obj);
        }

        if (clazz == BigInteger.class) {
            return (T) castToBigInteger(obj);
        }

        if (clazz == Date.class) {
            return (T) castToDate(obj);
        }

        if (clazz == java.sql.Date.class) {
            return (T) castToSqlDate(obj);
        }

        if (clazz == java.sql.Timestamp.class) {
            return (T) castToTimestamp(obj);
        }

        if (clazz.isEnum()) {
            return (T) castToEnum(obj, clazz);
        }

        if (Calendar.class.isAssignableFrom(clazz)) {
            Date date = castToDate(obj);
            Calendar calendar;
            if (clazz == Calendar.class) {
                calendar = Calendar.getInstance();
            } else {
                try {
                    calendar = (Calendar) clazz.newInstance();
                } catch (Exception e) {
                    throw new IllegalArgumentException("can not cast to : " + clazz.getName(), e);
                }
            }
            calendar.setTime(date);
            return (T) calendar;
        }

        if (obj instanceof String) {
            String strVal = (String) obj;

            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }

            if (clazz == Currency.class) {
                return (T) Currency.getInstance(strVal);
            }

            if (clazz == Locale.class) {
                return (T) toLocale(strVal);
            }
        }

        return null;
//        throw new IllegalArgumentException("can not cast to : " + clazz.getName());
    }

    public static Locale toLocale(String strVal) {
        String[] items = strVal.split("_");

        if (items.length == 1) {
            return new Locale(items[0]);
        }

        if (items.length == 2) {
            return new Locale(items[0], items[1]);
        }

        return new Locale(items[0], items[1], items[2]);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T castToEnum(Object obj, Class<T> clazz) {
        try {
            if (obj instanceof String) {
                String name = (String) obj;
                if (name.length() == 0) {
                    return null;
                }

                return (T) Enum.valueOf((Class<? extends Enum>) clazz, name);
            }

            if (obj instanceof Number) {
                int ordinal = ((Number) obj).intValue();
                Object[] values = clazz.getEnumConstants();
                if (ordinal < values.length) {
                    return (T) values[ordinal];
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("can not cast to : " + clazz.getName(), ex);
        }

        throw new IllegalArgumentException("can not cast to : " + clazz.getName());
    }

    @SuppressWarnings("unchecked")
    public static <T> T cast(Object obj, Type type) {
        if (obj == null) {
            return null;
        }

        if (type instanceof Class) {
            return (T) cast(obj, (Class<T>) type);
        }

        if (type instanceof ParameterizedType) {
            return (T) cast(obj, (ParameterizedType) type);
        }

        if (obj instanceof String) {
            String strVal = (String) obj;
            if (strVal.length() == 0 //
                || "null".equals(strVal) //
                || "NULL".equals(strVal)) {
                return null;
            }
        }

        if (type instanceof TypeVariable) {
            return (T) obj;
        }

        throw new IllegalArgumentException("can not cast to : " + type);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> T cast(Object obj, ParameterizedType type) {
        Type rawTye = type.getRawType();

        if (rawTye == Set.class || rawTye == HashSet.class //
            || rawTye == TreeSet.class //
            || rawTye == List.class //
            || rawTye == ArrayList.class) {
            Type itemType = type.getActualTypeArguments()[0];

            if (obj instanceof Iterable) {
                Collection collection;
                if (rawTye == Set.class || rawTye == HashSet.class) {
                    collection = new HashSet();
                } else if (rawTye == TreeSet.class) {
                    collection = new TreeSet();
                } else {
                    collection = new ArrayList();
                }

                for (Iterator it = ((Iterable) obj).iterator(); it.hasNext();) {
                    Object item = it.next();
                    collection.add(cast(item, itemType));
                }

                return (T) collection;
            }
        }

        if (rawTye == Map.class || rawTye == HashMap.class) {
            Type keyType = type.getActualTypeArguments()[0];
            Type valueType = type.getActualTypeArguments()[1];

            if (obj instanceof Map) {
                Map map = new HashMap();

                for (Map.Entry entry : ((Map<?, ?>) obj).entrySet()) {
                    Object key = cast(entry.getKey(), keyType);
                    Object value = cast(entry.getValue(), valueType);

                    map.put(key, value);
                }

                return (T) map;
            }
        }

        if (obj instanceof String) {
            String strVal = (String) obj;
            if (strVal.length() == 0) {
                return null;
            }
        }

        if (type.getActualTypeArguments().length == 1) {
            Type argType = type.getActualTypeArguments()[0];
            if (argType instanceof WildcardType) {
                return (T) cast(obj, rawTye);
            }
        }

        throw new IllegalArgumentException("can not cast to : " + type);
    }

    private static ConcurrentMap<String, Class<?>> mappings = new ConcurrentHashMap<String, Class<?>>(16, 0.75f, 1);

    static {
        addBaseClassMappings();
    }

    private static void addBaseClassMappings() {
        mappings.put("byte", byte.class);
        mappings.put("short", short.class);
        mappings.put("int", int.class);
        mappings.put("long", long.class);
        mappings.put("float", float.class);
        mappings.put("double", double.class);
        mappings.put("boolean", boolean.class);
        mappings.put("char", char.class);

        mappings.put("[byte", byte[].class);
        mappings.put("[short", short[].class);
        mappings.put("[int", int[].class);
        mappings.put("[long", long[].class);
        mappings.put("[float", float[].class);
        mappings.put("[double", double[].class);
        mappings.put("[boolean", boolean[].class);
        mappings.put("[char", char[].class);

        mappings.put("[B", byte[].class);
        mappings.put("[S", short[].class);
        mappings.put("[I", int[].class);
        mappings.put("[J", long[].class);
        mappings.put("[F", float[].class);
        mappings.put("[D", double[].class);
        mappings.put("[C", char[].class);
        mappings.put("[Z", boolean[].class);

        Class<?>[] classes = new Class[] {
                Object.class,
                Cloneable.class,
                loadClass("java.lang.AutoCloseable"),
                Exception.class,
                RuntimeException.class,
                IllegalAccessError.class,
                IllegalAccessException.class,
                IllegalArgumentException.class,
                IllegalMonitorStateException.class,
                IllegalStateException.class,
                IllegalThreadStateException.class,
                IndexOutOfBoundsException.class,
                InstantiationError.class,
                InstantiationException.class,
                InternalError.class,
                InterruptedException.class,
                LinkageError.class,
                NegativeArraySizeException.class,
                NoClassDefFoundError.class,
                NoSuchFieldError.class,
                NoSuchFieldException.class,
                NoSuchMethodError.class,
                NoSuchMethodException.class,
                NullPointerException.class,
                NumberFormatException.class,
                OutOfMemoryError.class,
                SecurityException.class,
                StackOverflowError.class,
                StringIndexOutOfBoundsException.class,
                TypeNotPresentException.class,
                VerifyError.class,
                StackTraceElement.class,
                HashMap.class,
                Hashtable.class,
                TreeMap.class,
                IdentityHashMap.class,
                WeakHashMap.class,
                LinkedHashMap.class,
                HashSet.class,
                LinkedHashSet.class,
                TreeSet.class,
                java.util.concurrent.TimeUnit.class,
                ConcurrentHashMap.class,
                loadClass("java.util.concurrent.ConcurrentSkipListMap"),
                loadClass("java.util.concurrent.ConcurrentSkipListSet"),
                java.util.concurrent.atomic.AtomicInteger.class,
                java.util.concurrent.atomic.AtomicLong.class,
                Collections.EMPTY_MAP.getClass(),
                BitSet.class,
                Calendar.class,
                Date.class,
                Locale.class,
                UUID.class,
                java.sql.Time.class,
                java.sql.Date.class,
                java.sql.Timestamp.class,
                SimpleDateFormat.class
//                com.alibaba.fastjson.JSONObject.class,
        };

        for (Class clazz : classes) {
            if (clazz == null) {
                continue;
            }
            mappings.put(clazz.getName(), clazz);
        }

        String[] awt = new String[] {
                "java.awt.Rectangle",
                "java.awt.Point",
                "java.awt.Font",
                "java.awt.Color"};
        for (String className : awt) {
            Class<?> clazz = loadClass(className);
            if (clazz == null) {
                break;
            }
            mappings.put(clazz.getName(), clazz);
        }

        String[] spring = new String[] {
                "org.springframework.util.LinkedMultiValueMap",
                "org.springframework.util.LinkedCaseInsensitiveMap",
                "org.springframework.remoting.support.RemoteInvocation",
                "org.springframework.remoting.support.RemoteInvocationResult"
        };
        for (String className : spring) {
            Class<?> clazz = loadClass(className);
            if (clazz == null) {
                break;
            }
            mappings.put(clazz.getName(), clazz);
        }
    }

    public static Class<?> loadClass(String className) {
        return loadClass(className, null);
    }
    
    public static Class<?> loadClass(String className, ClassLoader classLoader) {
        if (className == null || className.length() == 0) {
            return null;
        }

        Class<?> clazz = mappings.get(className);

        if (clazz != null) {
            return clazz;
        }

        if (className.charAt(0) == '[') {
            Class<?> componentType = loadClass(className.substring(1), classLoader);
            return Array.newInstance(componentType, 0).getClass();
        }

        if (className.startsWith("L") && className.endsWith(";")) {
            String newClassName = className.substring(1, className.length() - 1);
            return loadClass(newClassName, classLoader);
        }

        try {
            if (classLoader != null) {
                clazz = classLoader.loadClass(className);
                mappings.put(className, clazz);

                return clazz;
            }
        } catch (Throwable e) {
            // skip
        }

        try {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

            if (contextClassLoader != null && contextClassLoader != classLoader) {
                clazz = contextClassLoader.loadClass(className);
                mappings.put(className, clazz);

                return clazz;
            }
        } catch (Throwable e) {
            // skip
        }

        try {
            clazz = Class.forName(className);
            mappings.put(className, clazz);

            return clazz;
        } catch (Throwable e) {
            // skip
        }

        return clazz;
    }

    public static Class<?> getClass(Type type) {
        if (type.getClass() == Class.class) {
            return (Class<?>) type;
        }

        if (type instanceof ParameterizedType) {
            return getClass(((ParameterizedType) type).getRawType());
        }

        if (type instanceof TypeVariable) {
            Type boundType = ((TypeVariable<?>) type).getBounds()[0];
            return (Class<?>) boundType;
        }

        return Object.class;
    }

    public static class TypeResult {
        private final Class<?> clazz;
        private final int index;
        private int dimension;

        public TypeResult(Class<?> clazz, int index, int dimension) {
            this.clazz= clazz;
            this.index = index;
            this.dimension = dimension;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public int getIndex() {
            return index;
        }

        public int getDimension() {
            return dimension;
        }

        public void incrementDimension(int inc) {
            dimension += inc;
        }
    }

}
