package com.github.netty.protocol.servlet;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import com.github.netty.protocol.servlet.util.ServletUtil;

import javax.servlet.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The servlet filter chain
 * @author wangzihao
 */
public class ServletFilterChain implements FilterChain, Recyclable {

    /**
     * Consider that each request is handled by only one thread, and that the ServletContext will create a new SimpleFilterChain object on each request
     * therefore, the FilterChain's Iterator is used as a private variable of the FilterChain, without thread safety problems
     */
    private List<ServletFilterRegistration> filterRegistrationList = new ArrayList<>(16);
    private ServletRegistration servletRegistration;
    private ServletContext servletContext;
    private int pos;

//    public static final Set<Filter> FILTER_SET = new HashSet<>();
//    public static final AtomicLong SERVLET_TIME = new AtomicLong();
//    public static final AtomicLong FILTER_TIME = new AtomicLong();
//    private long beginTime;

    private static final Recycler<ServletFilterChain> RECYCLER = new Recycler<>(ServletFilterChain::new);

    protected ServletFilterChain(){}

    public static ServletFilterChain newInstance(ServletContext servletContext, ServletRegistration servletRegistration) {
        ServletFilterChain instance = RECYCLER.getInstance();
        instance.servletContext = servletContext;
        instance.servletRegistration = servletRegistration;
//        instance.beginTime = System.currentTimeMillis();
        return instance;
    }

    /**
     * each Filter calls the FilterChain method after processing the request.
     * this should find the next Filter, call its doFilter() method.
     * if there is no next one, you should call the servlet's service() method
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();

        if(pos == 0){
            ServletHttpServletRequest httpServletRequest = ServletUtil.unWrapper(request);
            httpServletRequest.setMultipartConfigElement(servletRegistration.getMultipartConfigElement());
            httpServletRequest.setServletSecurityElement(servletRegistration.getServletSecurityElement());

            //Initialization Servlet
            if(servletRegistration.isInitServletCas(false,true)){
                servletRegistration.getServlet().init(servletRegistration.getServletConfig());
            }
            if(listenerManager.hasServletRequestListener()) {
                listenerManager.onServletRequestInitialized(new ServletRequestEvent(servletContext,request));
            }
        }

        if(pos < filterRegistrationList.size()){
            ServletFilterRegistration filterRegistration = filterRegistrationList.get(pos);
            pos++;
            Filter filter = filterRegistration.getFilter();
            filter.doFilter(request, response, this);

//            FILTER_SET.add(filter);
        }else {
            try {
//                long filterEndTime = System.currentTimeMillis();
//                FILTER_TIME.addAndGet(filterEndTime - beginTime);

                servletRegistration.getServlet().service(request, response);

//                SERVLET_TIME.addAndGet(System.currentTimeMillis() - filterEndTime);
            }finally {
                if(listenerManager.hasServletRequestListener()) {
                    listenerManager.onServletRequestDestroyed(new ServletRequestEvent(servletContext,request));
                }

                //Recycling itself
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
