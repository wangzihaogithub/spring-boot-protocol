package com.github.netty.protocol.servlet.util;

import com.github.netty.core.util.AntPathMatcher;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

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
        synchronized (lock) {
            for (Element<T> tElement : array) {
                if (Objects.equals(tElement.objectName, objectName)) {
                    return;
                }
            }
        }
        if (isMatchAfter) {
            add(element);
        } else {
            addBefore(element);
        }
    }

    /**
     * Add multiple mapping objects
     *
     * @param list                      add in list
     * @param dispatcherType            current dispatcherType
     * @param relativePathNoQueryString relativePathNoQueryString
     */
    public void addMappingObjects(String relativePathNoQueryString, DispatcherType dispatcherType, List<Element<T>> list) {
        for (Element<T> element : this.array) {
            if (element.dispatcherTypes != null && !element.dispatcherTypes.contains(dispatcherType)) {
                continue;
            }
            if (ServletUtil.matchFiltersURL(element.normOriginalPattern, relativePathNoQueryString)
                    || antPathMatcher.match(element.normOriginalPattern, relativePathNoQueryString, "*")) {
                list.add(element);
            }
        }
    }

    public static class Element<T> {
        String normOriginalPattern;
        String originalPattern;
        T object;
        String objectName;
        String rootPath;
        boolean allPatternFlag;
        EnumSet<DispatcherType> dispatcherTypes;

        public Element(String objectName, T object) {
            this.objectName = objectName;
            this.object = object;
        }

        public Element(String rootPath, String originalPattern, T object, String objectName, EnumSet<DispatcherType> dispatcherTypes) {
            this.dispatcherTypes = dispatcherTypes;
            this.allPatternFlag = originalPattern.isEmpty()
                    || "/*".equals(originalPattern)
                    || "*".equals(originalPattern)
                    || "/**".equals(originalPattern);
            String normOriginalPattern = ServletUtil.normPrefixPath(ServletUtil.normSuffixPath(originalPattern));
            this.normOriginalPattern = allPatternFlag ? "/*" : normOriginalPattern;
            this.rootPath = rootPath;
            this.originalPattern = originalPattern;
            this.object = object;
            this.objectName = objectName;
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
                    '}';
        }

    }
}