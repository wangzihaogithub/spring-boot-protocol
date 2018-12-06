package com.github.netty.register.servlet;

import com.github.netty.core.constants.VersionConstants;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.Wrapper;
import io.netty.handler.codec.http.Cookie;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用于兼容 netty4 与netty5
 * @author acer01
 * 2018/7/28/028
 */
public class NettyHttpCookie implements Cookie,Wrapper<Cookie> {
    
    private Cookie source;
    private Class sourceClass;
    private final Object lock = new Object();
    
    private List<Method> getNameMethodList;
    private List<Method> getValueMethodList;
    private List<Method> getDomainMethodList;
    private List<Method> getPathMethodList;
    private List<Method> getCommentMethodList;
    private List<Method> getMaxAgeMethodList;
    private List<Method> getVersionAgeMethodList;
    private List<Method> getCommentUrlMethodList;
    private List<Method> getPortsMethodList;
    private List<Method> getRawValueMethodList;
    private List<Method> setRawValueMethodList;

    public NettyHttpCookie() {
    }

    public NettyHttpCookie(Cookie source) {
        wrap(source);
    }

    public String getName() {
        if(!VersionConstants.isEnableVersionAdapter()){
            return source.getName();
        }
        if(getNameMethodList == null){
            synchronized (lock) {
                if(getNameMethodList == null) {
                    getNameMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "getName",0),
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "name",0)
                    );
                }
            }
        }
        return (String) ReflectUtil.invokeMethodOnce(source, getNameMethodList);
    }

    public String getValue() {
        if(!VersionConstants.isEnableVersionAdapter()){
            return source.getValue();
        }
        if(getValueMethodList == null){
            synchronized (lock) {
                if(getValueMethodList == null) {
                    getValueMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "getValue",0),
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "value",0)
                    );
                }
            }
        }
        return (String) ReflectUtil.invokeMethodOnce(source, getValueMethodList);
    }

    public String getDomain() {
        if(!VersionConstants.isEnableVersionAdapter()){
            return source.getDomain();
        }
        if(getDomainMethodList == null){
            synchronized (lock) {
                if(getDomainMethodList == null) {
                    getDomainMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "getDomain",0),
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "domain",0)
                    );
                }
            }
        }
        return (String) ReflectUtil.invokeMethodOnce(source, getDomainMethodList);
    }

    public String getPath() {
        if(!VersionConstants.isEnableVersionAdapter()){
            return source.getPath();
        }
        if(getPathMethodList == null){
            synchronized (lock) {
                if(getPathMethodList == null) {
                    getPathMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "getPath",0),
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "path",0)
                    );
                }
            }
        }
        return (String) ReflectUtil.invokeMethodOnce(source, getPathMethodList);
    }

    public String getComment() {
        if(!VersionConstants.isEnableVersionAdapter()){
            return source.getComment();
        }
        if(getCommentMethodList == null){
            synchronized (lock) {
                if(getCommentMethodList == null) {
                    getCommentMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "getComment",0),
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "comment",0)
                    );
                }
            }
        }
        return (String) ReflectUtil.invokeMethodOnce(source, getCommentMethodList);
    }
  
    public long getMaxAge() {
        if(!VersionConstants.isEnableVersionAdapter()){
            return source.getMaxAge();
        }
        if(getMaxAgeMethodList == null){
            synchronized (lock) {
                if(getMaxAgeMethodList == null) {
                    getMaxAgeMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "getMaxAge",0),
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "maxAge",0)
                    );
                }
            }
        }
        return (long) ReflectUtil.invokeMethodOnce(source, getMaxAgeMethodList);
    }

    public int getVersion() {
        if(!VersionConstants.isEnableVersionAdapter()){
            return source.getVersion();
        }
        if(getVersionAgeMethodList == null){
            synchronized (lock) {
                if(getVersionAgeMethodList == null) {
                    getVersionAgeMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "getVersion",0),
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "version",0)
                    );
                }
            }
        }
        return (int) ReflectUtil.invokeMethodOnce(source, getVersionAgeMethodList);
    }

    public String getCommentUrl() {
        if(!VersionConstants.isEnableVersionAdapter()){
            return source.getCommentUrl();
        }
        if(getCommentUrlMethodList == null){
            synchronized (lock) {
                if(getCommentUrlMethodList == null) {
                    getCommentUrlMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "getCommentUrl",0),
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "commentUrl",0)
                    );
                }
            }
        }
        return (String) ReflectUtil.invokeMethodOnce(source, getCommentUrlMethodList);
    }

    public Set<Integer> getPorts() {
        if(!VersionConstants.isEnableVersionAdapter()){
            return source.getPorts();
        }
        if(getPortsMethodList == null){
            synchronized (lock) {
                if(getPortsMethodList == null) {
                    getPortsMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "getPorts",0),
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "ports",0)
                    );
                }
            }
        }
        return (Set<Integer>) ReflectUtil.invokeMethodOnce(source, getPortsMethodList);
    }

    public String rawValue() {
//        if(!VersionConstants.isEnableVersionAdapter()){
//            return source.rawValue();
//        }
        if(getRawValueMethodList == null){
            synchronized (lock) {
                if(getRawValueMethodList == null) {
                    getRawValueMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "rawValue",0)
                    );
                }
            }
        }
        return (String) ReflectUtil.invokeMethodOnce(source, getRawValueMethodList);
    }

    public void setRawValue(String rawValue) {
//        if(!VersionConstants.isEnableVersionAdapter()){
//            return source.setRawValue(rawValue);
//        }
        if(setRawValueMethodList == null){
            synchronized (lock) {
                if(setRawValueMethodList == null) {
                    setRawValueMethodList = Arrays.asList(
                            ReflectUtil.getAccessibleMethodByName(sourceClass, "setRawValue",1)
                    );
                }
            }
        }
        ReflectUtil.invokeMethodOnce(source, setRawValueMethodList,rawValue);
    }

    public boolean isSecure() {
        return source.isSecure();
    }

    public boolean isHttpOnly() {
        return source.isHttpOnly();
    }

    public boolean isDiscard() {
        return source.isDiscard();
    }

    public String name() {
        return getName();
    }

    public String value() {
        return getValue();
    }

    public String domain() {
        return getDomain();
    }

    public String path() {
        return getPath();
    }

    public String comment() {
        return getComment();
    }

    public long maxAge() {
        return getMaxAge();
    }

    public int version() {
        return getVersion();
    }

    public String commentUrl() {
        return getCommentUrl();
    }

    public Set<Integer> ports() {
        return getPorts();
    }

    @Override
    public void setValue(String s) {
        source.setValue(s);
    }

    public boolean wrap() {
        Method method = ReflectUtil.getAccessibleMethod(sourceClass, "wrap");
        try {
            return (boolean) method.invoke(source);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setWrap(boolean b) {
        Method method = ReflectUtil.getAccessibleMethod(sourceClass, "setWrap",boolean.class);
        try {
            method.invoke(source,b);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDomain(String s) {
        source.setDomain(s);
    }

    @Override
    public void setPath(String s) {
        source.setPath(s);
    }

    @Override
    public void setComment(String s) {
        source.setComment(s);
    }

    @Override
    public void setMaxAge(long l) {
        source.setMaxAge(l);
    }

    @Override
    public void setVersion(int i) {
        source.setVersion(i);
    }

    @Override
    public void setSecure(boolean b) {
        source.setSecure(b);
    }

    @Override
    public void setHttpOnly(boolean b) {
        source.setHttpOnly(b);
    }

    @Override
    public void setCommentUrl(String s) {
        source.setCommentUrl(s);
    }

    @Override
    public void setDiscard(boolean b) {
        source.setDiscard(b);
    }

    @Override
    public void setPorts(int... ints) {
        source.setPorts(ints);
    }

    @Override
    public void setPorts(Iterable<Integer> iterable) {
        source.setPorts(iterable);
    }

    public int compareTo(Cookie o) {
        return source.compareTo(o);
    }

    public int compareTo(io.netty.handler.codec.http.cookie.Cookie o) {
        return source.compareTo(o);
    }

    @Override
    public void wrap(Cookie source) {
        Objects.requireNonNull(source);
        this.source = source;
        this.sourceClass = source.getClass();
    }

    @Override
    public Cookie unwrap() {
        return source;
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return source.equals(obj);
    }

    @Override
    public String toString() {
        return "NettyHttpCookie{" +
                "sourceClass=" + sourceClass +
                '}';
    }

}
