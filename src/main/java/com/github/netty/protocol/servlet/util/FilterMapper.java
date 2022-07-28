package com.github.netty.protocol.servlet.util;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import java.util.*;

/**
 * Filter mapping
 *
 * @author wangzihao
 * Created on 2017-08-25 11:32.
 */
public class FilterMapper<T> {
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final Object lock = new Object();
    private String rootPath;
    /**
     * The set of filter mappings for this application, in the order they
     * were defined in the deployment descriptor with additional mappings
     * added via the {@link ServletContext} possibly both before and after
     * those defined in the deployment descriptor.
     */
    private Element<T>[] array = new Element[0];

    /**
     * Filter mappings added via {@link ServletContext} may have to be
     * inserted before the mappings in the deployment descriptor but must be
     * inserted in the order the {@link ServletContext} methods are called.
     * This isn't an issue for the mappings added after the deployment
     * descriptor - they are just added to the end - but correctly the
     * adding mappings before the deployment descriptor mappings requires
     * knowing where the last 'before' mapping was added.
     */
    private int insertPoint = 0;

    public FilterMapper() {
        this.antPathMatcher.setCachePatterns(Boolean.TRUE);
    }

    public static String normPath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        while (path.startsWith("//")) {
            path = path.substring(1);
        }
        if (path.length() > 1) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
        }
        return path;
    }

    public static void main(String[] args) {
        FilterMapper<Object> urlMapper = new FilterMapper<>();
        urlMapper.addMapping("/t/", "", "default", false, null);
        urlMapper.addMapping("/t/", "", "1", false, null);
        urlMapper.addMapping("/t/", "", "2", false, null);
        urlMapper.addMapping("/*", "", "3", false, null);
        urlMapper.addMapping("/*.do", "", "4", false, null);

//        urlMapper.setRootPath("test");

        Element<Object> e1 = urlMapper.getMappingObjectByUri("/t/a/d");
        assert Objects.equals("1", e1.objectName);

        Element<Object> e2 = urlMapper.getMappingObjectByUri("/a");
        assert Objects.equals("3", e2.objectName);
    }

    public void clear() {
        synchronized (lock) {
            array = new Element[0];
        }
    }

    /**
     * Add a filter mapping at the end of the current set of filter
     * mappings.
     *
     * @param filterMap The filter mapping to be added
     */
    private void add(Element filterMap) {
        synchronized (lock) {
            Element[] results = Arrays.copyOf(array, array.length + 1);
            results[array.length] = filterMap;
            array = results;
        }
    }

    /**
     * Add a filter mapping before the mappings defined in the deployment
     * descriptor but after any other mappings added via this method.
     *
     * @param filterMap The filter mapping to be added
     */
    private void addBefore(Element filterMap) {
        synchronized (lock) {
            Element[] results = new Element[array.length + 1];
            System.arraycopy(array, 0, results, 0, insertPoint);
            System.arraycopy(array, insertPoint, results, insertPoint + 1,
                    array.length - insertPoint);
            results[insertPoint] = filterMap;
            array = results;
            insertPoint++;
        }
    }

    /**
     * Remove a filter mapping.
     *
     * @param filterMap The filter mapping to be removed
     */
    public void remove(Element filterMap) {
        synchronized (lock) {
            // Make sure this filter mapping is currently present
            int n = -1;
            for (int i = 0; i < array.length; i++) {
                if (array[i] == filterMap) {
                    n = i;
                    break;
                }
            }
            if (n < 0) {
                return;
            }

            // Remove the specified filter mapping
            Element[] results = new Element[array.length - 1];
            System.arraycopy(array, 0, results, 0, n);
            System.arraycopy(array, n + 1, results, n, (array.length - 1)
                    - n);
            array = results;
            if (n < insertPoint) {
                insertPoint--;
            }
        }
    }

    public void setRootPath(String rootPath) {
        while (rootPath.startsWith("/")) {
            rootPath = rootPath.substring(1);
        }
        rootPath = "/" + rootPath;
        synchronized (lock) {
            Element<T>[] newElements = new Element[array.length];
            for (int i = 0; i < this.array.length; i++) {
                Element<T> source = array[i];

                Element<T> element = new Element<>(rootPath, source.originalPattern, source.object, source.objectName, source.dispatcherTypes);
                newElements[i] = element;
            }
            this.rootPath = rootPath;
            this.array = newElements;
        }
    }

    /**
     * Add mapping
     *
     * @param urlPattern      urlPattern
     * @param object          object
     * @param objectName      objectName
     * @param isMatchAfter    isMatchAfter
     * @param dispatcherTypes dispatcherTypes
     * @throws IllegalArgumentException IllegalArgumentException
     */
    public void addMapping(String urlPattern, T object, String objectName, boolean isMatchAfter, EnumSet<DispatcherType> dispatcherTypes) throws IllegalArgumentException {
        Objects.requireNonNull(urlPattern);

        Element<T> element = new Element<>(rootPath, urlPattern, object, objectName, dispatcherTypes);
        if (isMatchAfter) {
            add(element);
        } else {
            addBefore(element);
        }
    }

    /**
     * Gets a servlet path
     *
     * @param absoluteUri An absolute path
     * @return servlet path
     */
    public String getServletPath(String absoluteUri) {
        String path = normPath(absoluteUri);
        for (Element<T> element : array) {
            if (antPathMatcher.match(element.pattern, path, "*")) {
                return element.servletPath;
            }
        }
        return absoluteUri;
    }

    /**
     * Gets a mapping object
     *
     * @param absoluteUri An absolute path
     * @return T object
     */
    public Element<T> getMappingObjectByUri(String absoluteUri) {
        String path = normPath(absoluteUri);
        for (Element<T> element : this.array) {
            if (antPathMatcher.match(element.pattern, path, "*")) {
                return element;
            }
        }
        return null;
    }

    /**
     * Add multiple mapping objects
     *
     * @param list           add in list
     * @param dispatcherType current dispatcherType
     * @param absoluteUri    An absolute path
     */
    public void addMappingObjectsByUri(String absoluteUri, DispatcherType dispatcherType, List<Element<T>> list) {
        String path = normPath(absoluteUri);
        for (Element<T> element : this.array) {
            if (element.dispatcherTypes != null && !element.dispatcherTypes.contains(dispatcherType)) {
                continue;
            }
            if (antPathMatcher.match(element.pattern, path, "*")) {
                list.add(element);
            }
        }
    }

    public static class Element<T> {
        String pattern;
        String originalPattern;
        T object;
        String objectName;
        String servletPath;
        String rootPath;
        boolean wildcardPatternFlag;
        boolean allPatternFlag;
        boolean defaultFlag;
        EnumSet<DispatcherType> dispatcherTypes;

        public Element(String objectName, T object) {
            this.objectName = objectName;
            this.object = object;
        }

        public Element(String rootPath, String originalPattern, T object, String objectName, EnumSet<DispatcherType> dispatcherTypes) {
            this.dispatcherTypes = dispatcherTypes;
            this.allPatternFlag = "/".equals(originalPattern)
                    || "/*".equals(originalPattern)
                    || "*".equals(originalPattern)
                    || "/**".equals(originalPattern);
            if (rootPath != null) {
                this.pattern = rootPath.concat(originalPattern);
            } else {
                this.pattern = originalPattern;
            }
            if (pattern.endsWith("/")) {
                do {
                    pattern = pattern.substring(0, pattern.length() - 1);
                } while (pattern.endsWith("/"));
                pattern = pattern + "/*";
            }

            this.pattern = normPath(this.pattern);
            this.rootPath = rootPath;
            this.originalPattern = originalPattern;
            this.object = object;
            this.objectName = objectName;
            StringJoiner joiner = new StringJoiner("/");
            String[] pattens = pattern.split("/");
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
            this.servletPath = joiner.toString();
        }

        public EnumSet<DispatcherType> getDispatcherTypes() {
            return dispatcherTypes;
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
                    '}';
        }

    }
}