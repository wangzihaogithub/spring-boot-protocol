package com.github.netty.protocol.servlet.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Url mapping
 *
 * Mapping specification
 * In the web application deployment descriptor, the following syntax is used to define the mapping:
 * ■  A string starting with the '/' character and ending with the '/*' suffix is used for path matching.
 * ■  The string starting with '*.' is used for extension mapping.
 * ■  The empty string "" is a special URL pattern that maps exactly to the context root of the application, http://host:port/context-root/
 * Request form. In this case, the path information is'/' and the servlet path and context path are empty strings (" ").
 * ■  A string containing only the "/" character represents the applied "default" servlet. In this case, the servlet path is the request URL minus
 * The following path and path information is null。
 * ■  So other strings are used only for exact matches。
 * Deployment will fail if a valid web.xml (after merging information from fragments and annotations) contains arbitrary url-patterns mapped to multiple servlets.
 *
 *
 * Sample mapping set
 * Look at the following set of mappings：
 *  Table 12-1 sample mapping set
 *      Path Pattern            Servlet
 *
 *      /foo/bar/*              servlet1
 *      /baz/*                  servlet2
 *      /catalog                servlet3
 *      *.bop                   servlet4
 * Will produce the following behavior：
 *  Table 12-2. The incoming path is applied to the sample map
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
 * Note that in the case of /catalog/index.html and /catalog/racecar.bop, the servlet mapped to "/catalog" is not used because it is not an exact match
 *
 * @author wangzihao
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
     * Add mapping
     * @param urlPattern  urlPattern
     * @param object     object
     * @param objectName objectName
     * @throws IllegalArgumentException IllegalArgumentException
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
     * Delete the mapping
     * @param objectName objectName
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
     * Gets a servlet path
     * @param absoluteUri An absolute path
     * @return servlet path
     */
    public String getServletPath(String absoluteUri) {
        int size = elementList.size();
        for(int i=0; i<size; i++){
            Element element = elementList.get(i);
            if(antPathMatcher.match(element.pattern,absoluteUri,"*")){
                return element.pattern
                        .replace("/*","")
                        .replace("/**","");
            }
        }
        return absoluteUri;
    }

    /**
     * Gets a mapping object
     * @param absoluteUri An absolute path
     * @return T object
     */
    public T getMappingObjectByUri(String absoluteUri) {
        int size = elementList.size();
        for(int i=0; i<size; i++){
            Element element = elementList.get(i);
            if(antPathMatcher.match(element.pattern,absoluteUri,"*")){
                return element.object;
            }
        }
        for(int i=0; i<size; i++){
            Element element = elementList.get(i);
            if('/' == element.pattern.charAt(0)
                    || '*' == element.pattern.charAt(0)
                    || "/*".equals(element.pattern)
                    || "/**".equals(element.pattern)){
                return element.object;
            }
        }
        return null;
    }

    /**
     * Add multiple mapping objects
     * @param list add in list
     * @param absoluteUri An absolute path
     */
    public void addMappingObjectsByUri(String absoluteUri, List<T> list) {
        int size = elementList.size();
        for(int i=0; i<size; i++){
            Element element = elementList.get(i);
            if("/*".equals(element.pattern)
                    ||(absoluteUri.length() == 1 && '/' == absoluteUri.charAt(0) && '/' == element.pattern.charAt(0))
                    || '*' == element.pattern.charAt(0)
                    || "/**".equals(element.pattern)
                    || antPathMatcher.match(element.pattern,absoluteUri,"*")){
                list.add(element.object);
            }
        }
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