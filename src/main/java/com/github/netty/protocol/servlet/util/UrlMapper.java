package com.github.netty.protocol.servlet.util;

import com.github.netty.core.util.AntPathMatcher;

import java.util.*;

/**
 * Url mapping
 * <p>
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
 * <p>
 * <p>
 * Sample mapping set
 * Look at the following set of mappings：
 * Table 12-1 sample mapping set
 * Path Pattern            Servlet
 * <p>
 * /foo/bar/*              servlet1
 * /baz/*                  servlet2
 * /catalog                servlet3
 * *.bop                   servlet4
 * Will produce the following behavior：
 * Table 12-2. The incoming path is applied to the sample map
 * Incoming Path           Servlet Handling Request
 * <p>
 * /foo/bar/index.html     servlet1
 * /foo/bar/index.bop      servlet1
 * /baz                    servlet2
 * /baz/index.html         servlet2
 * /catalog                servlet3
 * /catalog/index.html    “default”  servlet
 * /catalog/racecar.bop    servlet4
 * /index.bop              servlet4
 * Note that in the case of /catalog/index.html and /catalog/racecar.bop, the servlet mapped to "/catalog" is not used because it is not an exact match
 *
 * @author wangzihao
 * Created on 2017-08-25 11:32.
 */
public class UrlMapper<T> {
    private AntPathMatcher antPathMatcher;
    private int sort = 0;
    private String rootPath = "";
    private final Collection<Element<T>> elementList = new TreeSet<>();

    public UrlMapper() {
    }

    public static void main(String[] args) {
        UrlMapper<Object> urlMapper = new UrlMapper<>();
        urlMapper.addMapping("/t/", "", "default");
        urlMapper.addMapping("/t/a*", "", "/t/a");
        urlMapper.addMapping("/t/a/*", "", "/t/a/");
        urlMapper.addMapping("/t/", "", "1");
        urlMapper.addMapping("/t/a", "", "22");
        urlMapper.addMapping("/t/a/", "", "33");
        urlMapper.addMapping("/t/a1", "", "44");
        urlMapper.addMapping("/t/", "", "2");
        urlMapper.addMapping("/*", "", "3");
        urlMapper.addMapping("/*.do", "", "4");

        urlMapper.setRootPath("test");

        Element<Object> e1 = urlMapper.getMappingObjectByServletPath("/t/a/d");
        assert Objects.equals("1", e1.objectName);
//
        Element<Object> e2 = urlMapper.getMappingObjectByServletPath("/a");
        assert Objects.equals("3", e2.objectName);
    }

    public void clear() {
        elementList.clear();
    }

    public void setEnableAntPathMatcher(boolean enableAntPathMatcher) {
        if (enableAntPathMatcher) {
            antPathMatcher = new AntPathMatcher();
            antPathMatcher.setCachePatterns(Boolean.TRUE);
        } else {
            antPathMatcher = null;
        }
    }

    public boolean isEnableAntPathMatcher() {
        return antPathMatcher != null;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
        ArrayList<Element<T>> elements = new ArrayList<>(this.elementList);
        elementList.clear();
        for (Element<T> element : elements) {
            elementList.add(new Element<>(rootPath, element.originalPattern, element.object, element.objectName, sort++));
        }
    }

    /**
     * Add mapping
     *
     * @param urlPattern urlPattern
     * @param object     object
     * @param objectName objectName
     * @throws IllegalArgumentException IllegalArgumentException
     */
    public void addMapping(String urlPattern, T object, String objectName) throws IllegalArgumentException {
        Objects.requireNonNull(urlPattern);
        Objects.requireNonNull(object);
        Objects.requireNonNull(objectName);
        for (Element element : elementList) {
            if (element.objectName.equals(objectName)) {
                throw new IllegalArgumentException("The [" + objectName + "] mapping exist!");
            }
        }
        elementList.add(new Element<>(rootPath, urlPattern, object, objectName, sort++));
    }

    /**
     * getMappingObjectByRelativeUri
     *
     * @param relativePathNoQueryString contextRelativeRequestPath
     * @return T object
     */
    public Element<T> getMappingObjectByServletPath(String relativePathNoQueryString) {
        Collection<Element<T>> elementList = this.elementList;
        AntPathMatcher antPathMatcher = this.antPathMatcher;
        for (Element<T> element : elementList) {
            if (ServletUtil.matchFiltersURL(element.normOriginalPattern, relativePathNoQueryString)) {
                return element;
            } else if (antPathMatcher != null && antPathMatcher.match(element.normOriginalPattern, relativePathNoQueryString, "*")) {
                return element;
            }
        }
        return null;
    }

    public static class Element<T> implements Comparable<Element<T>> {
        String normOriginalPattern;
        String originalPattern;
        T object;
        String objectName;
        String servletPath;
        String rootPath;
        boolean startPatternFlag;
        boolean wildcardPatternFlag;
        boolean allPatternFlag;
        boolean defaultFlag;
        int sort;
        int addSort;
        int firstWildcardIndex = -1;

        public Element(String rootPath, String originalPattern, T object, String objectName, int addSort) {
            this.addSort = addSort;
            this.allPatternFlag = originalPattern.isEmpty()
                    || "/".equals(originalPattern)
                    || "/*".equals(originalPattern)
                    || "*".equals(originalPattern)
                    || "/**".equals(originalPattern);
            String normOriginalPattern = ServletUtil.normPrefixPath(ServletUtil.normSuffixPath(originalPattern));
            this.startPatternFlag = normOriginalPattern.startsWith("*");
            this.normOriginalPattern = allPatternFlag ? "/*" : normOriginalPattern;
            this.rootPath = rootPath;
            this.originalPattern = originalPattern;
            this.object = object;
            this.objectName = objectName;
            int tokens = 0;
            for (int i = 0; i < normOriginalPattern.length(); i++) {
                if (normOriginalPattern.charAt(i) == '/') {
                    tokens++;
                }
            }
            StringJoiner joiner = new StringJoiner("/");
            String[] pattens = normOriginalPattern.split("/");
            for (int i = 0; i < pattens.length; i++) {
                String path = pattens[i];
                if (path.contains("*")) {
                    wildcardPatternFlag = true;
                    if (i == pattens.length - 1) {
                        continue;
                    }
                }
                joiner.add(path);
            }
            this.defaultFlag = "default".equals(this.objectName);
            this.servletPath = startPatternFlag ? "" : joiner.toString();
            if (this.defaultFlag) {
                this.sort = 500000 + addSort;
            } else if (this.allPatternFlag) {
                this.sort = 400000 + addSort;
            } else if (wildcardPatternFlag) {
                this.sort = 300000 + addSort;
            } else {
                this.sort = 200000 - Math.min(tokens * 100, 200000) + addSort;
            }
            for (int i = 0, find = 0; i < normOriginalPattern.length(); i++) {
                char c = normOriginalPattern.charAt(i);
                if (c == '/') {
                    continue;
                }
                find++;
                if (c == '*') {
                    this.firstWildcardIndex = find;
                    break;
                }
            }
        }

        public boolean isStartPatternFlag() {
            return startPatternFlag;
        }

        public String getPathInfo(String relativePath, int decodePathsQueryIndex) {
            // case /  , !/api/*  , *.do
            if (allPatternFlag || !wildcardPatternFlag || startPatternFlag) {
                return null;
            }
            int firstWildcardIndex = this.firstWildcardIndex;
            if (decodePathsQueryIndex == -1) {
                return relativePath.substring(firstWildcardIndex);
            } else {
                return relativePath.substring(firstWildcardIndex, decodePathsQueryIndex);
            }
        }

        public String getServletPath(String relativePathNoQueryString) {
            if (startPatternFlag) {
                // case： *.do
                return "";
            } else if (allPatternFlag) {
                // case： /
                return relativePathNoQueryString;
            } else if (wildcardPatternFlag) {
                // case： /api/*
                return servletPath;
            } else {
                // case： /api
                return servletPath;
            }
        }

        public int getFirstWildcardIndex() {
            return firstWildcardIndex;
        }

        public boolean isAllPatternFlag() {
            return allPatternFlag;
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
                    "pattern='" + originalPattern + '\'' +
                    ", objectName='" + objectName + '\'' +
                    ", sort=" + sort +
                    '}';
        }

        @Override
        public int compareTo(Element<T> o) {
            return this.sort < o.sort ? -1 : 1;
        }
    }
}