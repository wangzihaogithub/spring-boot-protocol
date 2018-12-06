package com.github.netty.register.servlet;

import com.github.netty.core.util.AbstractRecycler;
import com.github.netty.core.util.Recyclable;

import javax.servlet.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * servlet过滤链
 *
 * 频繁更改, 需要cpu对齐. 防止伪共享, 需设置 : -XX:-RestrictContended
 * @author 84215
 */
@sun.misc.Contended
public class ServletFilterChain implements FilterChain,Recyclable {

    /**
     * 考虑到每个请求只有一个线程处理，而且ServletContext在每次请求时都会new 一个SimpleFilterChain对象
     * 所以这里把过滤器链的Iterator作为FilterChain的私有变量，没有线程安全问题
     */
    private List<ServletFilterRegistration> filterRegistrationList = new ArrayList<>(16);
    private ServletRegistration servletRegistration;
    private ServletContext servletContext;
    private int pos;

    public static final Set<Filter> FILTER_SET = new HashSet<>();
    public static final AtomicLong SERVLET_TIME = new AtomicLong();
    public static final AtomicLong FILTER_TIME = new AtomicLong();
    private long beginTime;

    private static final AbstractRecycler<ServletFilterChain> RECYCLER = new AbstractRecycler<ServletFilterChain>() {
        @Override
        protected ServletFilterChain newInstance() {
            return new ServletFilterChain();
        }
    };

    protected ServletFilterChain(){}

    public static ServletFilterChain newInstance(ServletContext servletContext, ServletRegistration servletRegistration) {
        ServletFilterChain instance = RECYCLER.getInstance();
        instance.servletContext = servletContext;
        instance.servletRegistration = servletRegistration;
        instance.beginTime = System.currentTimeMillis();
        return instance;
    }

    /**
     * 每个Filter在处理完请求之后调用FilterChain的这个方法。
     * 这时候应该找到下一个Filter，调用其doFilter()方法。
     * 如果没有下一个了，应该调用servlet的service()方法了
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();

        if(pos == 0){
            if(listenerManager.hasServletRequestListener()) {
                listenerManager.onServletRequestInitialized(new ServletRequestEvent(servletContext,request));
            }
        }

        if(pos < filterRegistrationList.size()){
            ServletFilterRegistration filterRegistration = filterRegistrationList.get(pos);
            pos++;
            Filter filter = filterRegistration.getFilter();
            filter.doFilter(request, response, this);

            FILTER_SET.add(filter);
        }else {
            try {
                long filterEndTime = System.currentTimeMillis();
                FILTER_TIME.addAndGet(filterEndTime - beginTime);

                servletRegistration.getServlet().service(request, response);

                SERVLET_TIME.addAndGet(System.currentTimeMillis() - filterEndTime);
            }finally {
                if(listenerManager.hasServletRequestListener()) {
                    listenerManager.onServletRequestDestroyed(new ServletRequestEvent(servletContext,request));
                }

                //回收异步请求
                if(request instanceof ServletHttpAsyncRequest){
                    ((ServletHttpAsyncRequest)request).getAsyncContext().recycle();
                }

                //回收自身
                recycle();
            }
        }
    }

    public ServletRegistration getServletRegistration() {
        return servletRegistration;
    }

    public List<ServletFilterRegistration> getFilterRegistrationList() {
        return filterRegistrationList;
    }

    @Override
    public void recycle() {
        pos = 0;
        servletContext = null;
        filterRegistrationList.clear();
        servletRegistration = null;
        RECYCLER.recycleInstance(this);
    }

}
