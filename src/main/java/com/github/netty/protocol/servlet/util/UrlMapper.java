package com.github.netty.protocol.servlet.util;

import java.util.*;

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
    private int sort = 0;
	private String rootPath;
    private Collection<Element<T>> elementList = new TreeSet<>();
    private final boolean singlePattern;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final Comparator<? super Element<T>> addSortComparator =(o1, o2) -> o1.addSort < o2.addSort ? -1 : 1;

    public UrlMapper(boolean singlePattern) {
        this.singlePattern = singlePattern;
        this.antPathMatcher.setCachePatterns(Boolean.TRUE);
    }

	public void setRootPath(String rootPath) {
        while (rootPath.startsWith("/")){
            rootPath = rootPath.substring(1);
        }
        rootPath = "/" + rootPath;

		this.rootPath = rootPath;
        Collection<Element<T>> elementList = new TreeSet<>();
		for(Element<T> element : this.elementList){
			elementList.add(new Element<>(rootPath,element.originalPattern,element.object,element.objectName,sort++));
		}
		this.elementList = elementList;
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
	    Collection<Element<T>> elementList = this.elementList;

        for (Element element : elementList) {
            if(singlePattern) {
                if(element.objectName.equals(objectName)) {
                    throw new IllegalArgumentException("The [" + objectName + "] mapping exist!");
                }
            }
        }
        elementList.add(new Element<>(rootPath,urlPattern,object,objectName,sort++));
    }

    /**
     * Delete the mapping
     * @param objectName objectName
     */
    public void removeMapping(String objectName) {
        Iterator<Element<T>> it = elementList.iterator();
        while (it.hasNext()){
            Element<T> element = it.next();
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
        Collection<Element<T>> elementList = this.elementList;
        for (Element<T> element : elementList) {
            if(antPathMatcher.match(element.pattern,absoluteUri,"*")){
                return element.servletPath;
            }
        }
        return absoluteUri;
    }

    /**
     * Gets a mapping object
     * @param absoluteUri An absolute path
     * @return T object
     */
    public Element<T> getMappingObjectByUri(String absoluteUri) {
        if(!absoluteUri.isEmpty() && absoluteUri.charAt(absoluteUri.length() - 1) == '/'){
            absoluteUri = absoluteUri.substring(0,absoluteUri.length()-1);
        }
	    Collection<Element<T>> elementList = this.elementList;
        for (Element<T> element : elementList) {
            if(antPathMatcher.match(element.pattern,absoluteUri,"*")){
                return element;
            }
        }
        return null;
    }

    /**
     * Add multiple mapping objects
     * @param list add in list
     * @param absoluteUri An absolute path
     */
    public void addMappingObjectsByUri(String absoluteUri, List<Element<T>> list) {
        if(!absoluteUri.isEmpty() && absoluteUri.charAt(absoluteUri.length() - 1) == '/'){
            absoluteUri = absoluteUri.substring(0,absoluteUri.length()-1);
        }
        Collection<Element<T>> elementList = this.elementList;
        for (Element<T> element : elementList) {
            if(antPathMatcher.match(element.pattern,absoluteUri,"*")){
                list.add(element);
            }
        }
        list.sort(addSortComparator);
    }

    public static class Element<T> implements Comparable<Element<T>>{
        String pattern;
	    String originalPattern;
        T object;
        String objectName;
        String servletPath;
        String rootPath;
        boolean wildcardPatternFlag;
        boolean allPatternFlag;
        boolean defaultFlag;
        int sort;
        int addSort;
        public Element(String objectName,T object){
            this.objectName = objectName;
            this.object = object;
        }
        public Element(String rootPath,String originalPattern, T object, String objectName,int addSort) {
            this.addSort = addSort;
            this.allPatternFlag = "/".equals(originalPattern)
                    || "/*".equals(originalPattern)
                    || "*".equals(originalPattern)
                    || "/**".equals(originalPattern);
        	if(rootPath != null){
                this.pattern = rootPath.concat(originalPattern);
	        }else {
        		this.pattern = originalPattern;
	        }
        	if(pattern.endsWith("/")){
        	    do {
                    pattern = pattern.substring(0,pattern.length() -1);
                }while(pattern.endsWith("/"));
                pattern = pattern + "/*";
            }

        	this.rootPath = rootPath;
            this.originalPattern = originalPattern;
            this.object = object;
            this.objectName = objectName;
	        StringJoiner joiner = new StringJoiner("/");
            String[] pattens = pattern.split("/");
            for(int i=0; i<pattens.length; i++){
                String path = pattens[i];
            	if(path.contains("*")){
                    wildcardPatternFlag = true;
                    if(i == pattens.length - 1) {
                        continue;
                    }
	            }
            	joiner.add(path);
            }
            this.defaultFlag = "default".equals(this.objectName);
            this.servletPath = joiner.toString();
            if(this.defaultFlag){
                this.sort = 300;
            }else if(this.allPatternFlag){
                this.sort = 200;
            }else {
                this.sort = 100;
            }
        }

        public boolean isWildcardPatternFlag() {
            return wildcardPatternFlag;
        }

        public T getObject() {
            return object;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getPattern() {
            return pattern;
        }

        public String getServletPath() {
            return servletPath;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public String toString() {
            return "Element{" +
                    "pattern='" + pattern + '\'' +
                    ", objectName='" + objectName + '\'' +
                    ", addSort=" + addSort +
                    '}';
        }

        @Override
        public int compareTo(Element<T> o) {
            return this.sort < o.sort ? -1 : 1;
        }
    }

    public static void main(String[] args) {
        UrlMapper<Object> urlMapper = new UrlMapper<>(false);
        urlMapper.addMapping("/t/","","default");
        urlMapper.addMapping("/t/","","1");
        urlMapper.addMapping("/t/","","2");
        urlMapper.addMapping("/*","","3");
        urlMapper.addMapping("/*.do","","4");

//        urlMapper.setRootPath("test");

        Element<Object> e1 = urlMapper.getMappingObjectByUri("/t/a/d");
        assert Objects.equals("1", e1.objectName);

        Element<Object> e2 = urlMapper.getMappingObjectByUri("/a");
        assert Objects.equals("3", e2.objectName);
    }
}