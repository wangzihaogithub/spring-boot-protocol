package com.github.netty.register.servlet.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * url映射
 *
 * 映射规范
 * 在web应用部署描述符中，以下语法用于定义映射：
 * ■  以‘/’字符开始、以‘/*’后缀结尾的字符串用于路径匹配。
 * ■  以‘*.’开始的字符串用于扩展名映射。
 * ■  空字符串“”是一个特殊的URL模式，其精确映射到应用的上下文根，即，http://host:port/context-root/
 * 请求形式。在这种情况下，路径信息是‘/’且servlet路径和上下文路径是空字符串（“”）。
 * ■  只包含“/”字符的字符串表示应用的“default”servlet。在这种情况下，servlet路径是请求URL减去上
 * 下文路径且路径信息是null。
 * ■  所以其他字符串仅用于精确匹配。
 * 如果一个有效的web.xml（在从fragment 和注解合并了信息后）包含人任意的url-pattern，其映射到多个servlet，那么部署将失败。
 *
 *
 * 示例映射集合
 * 请看下面的一组映射：
 *  表12-1  示例映射集合
 *      Path Pattern            Servlet
 *
 *      /foo/bar/*              servlet1
 *      /baz/*                  servlet2
 *      /catalog                servlet3
 *      *.bop                   servlet4
 * 将产生以下行为：
 *  表12-2   传入路径应用于示例映射
 *      Incoming Path           Servlet Handling Request
 *
 *      /foo/bar/index.html     servlet1
 *      /foo/bar/index.bop      servlet1
 *      /baz                    servlet2
 *      /baz/index.html         servlet2
 *      /catalog                servlet3
 *      /catalog/index.html    “default”  servlet
 *      /catalog/racecar.bop    servlet4
 *      /index.bop              servlet4
 * 请注意，在/catalog/index.html和/catalog/racecar.bop的情况下，不使用映射到“/catalog”的servlet，因为不是精确匹配的
 *
 * @author acer01
 * Created on 2017-08-25 11:32.
 */
public class UrlMapper<T> {

    private final boolean singlePattern;
    private List<Element> elementList = new ArrayList<>();
    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    public UrlMapper(boolean singlePattern) {
        this.singlePattern = singlePattern;
    }

    /**
     * 增加映射关系
     *
     * @param urlPattern  urlPattern
     * @param object     对象
     * @param objectName 对象名称
     * @throws IllegalArgumentException 异常
     */
    public void addMapping(String urlPattern, T object, String objectName) throws IllegalArgumentException {
        Objects.requireNonNull(urlPattern);
        Objects.requireNonNull(object);
        Objects.requireNonNull(objectName);

        for (Element element : elementList) {
            if(singlePattern && element.objectName.equals(objectName)) {
                throw new IllegalArgumentException("The [" + objectName + "] mapping exist!");
            }
            if(element.pattern.equals(urlPattern)) {
                element.objectName = objectName;
                element.object = object;
                return;
            }
        }
        elementList.add(new Element(urlPattern,object,objectName));
    }

    /**
     * 删除映射
     * @param objectName 对象名称
     */
    public void removeMapping(String objectName) {
        Iterator<Element> it = elementList.iterator();
        while (it.hasNext()){
            Element element = it.next();
            if(element.objectName.equals(objectName)){
                it.remove();
            }
        }
    }

    /**
     * 获取一个映射对象
     * @param absoluteUri 绝对路径
     * @return
     */
    public T getMappingObjectByUri(String absoluteUri) {
        int size = elementList.size();

        for(int i=0; i<size; i++){
            Element element = elementList.get(i);
            if('/' == element.pattern.charAt(0)
                    || '*' == element.pattern.charAt(0)
                    || "/*".equals(element.pattern)
                    || "/**".equals(element.pattern)
                    || antPathMatcher.match(element.pattern,absoluteUri)){
                return element.object;
            }
        }
        return null;
    }

    /**
     * 获取多个映射对象
     * @param absoluteUri 绝对路径
     * @return
     */
    public List<T> getMappingObjectsByUri(String absoluteUri,List<T> list) {
        int size = elementList.size();
        for(int i=0; i<size; i++){
            Element element = elementList.get(i);
            if("/*".equals(element.pattern)
                    ||'/' == element.pattern.charAt(0)
                    || '*' == element.pattern.charAt(0)
                    || "/**".equals(element.pattern)
                    || antPathMatcher.match(element.pattern,absoluteUri)){
                list.add(element.object);
            }
        }
        return list;
    }

    private class Element {
        String pattern;
        T object;
        String objectName;
        
        Element(String pattern, T object, String objectName) {
            this.pattern = pattern;
            this.object = object;
            this.objectName = objectName;
        }
    }

}