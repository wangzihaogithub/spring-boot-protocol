package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import com.github.netty.protocol.servlet.util.FilterMapper;
import com.github.netty.protocol.servlet.util.ServletUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The servlet filter chain
 *
 * @author wangzihao
 */
public class ServletFilterChain implements FilterChain, Recyclable {
    private static final LoggerX logger = LoggerFactoryX.getLogger(ServletEventListenerManager.class);
    private static final Recycler<ServletFilterChain> RECYCLER = new Recycler<>(ServletFilterChain::new);
    /**
     * Consider that each request is handled by only one thread, and that the ServletContext will create a new SimpleFilterChain object on each request
     * therefore, the FilterChain's Iterator is used as a private variable of the FilterChain, without thread safety problems
     */
    private List<FilterMapper.Element<ServletFilterRegistration>> filterRegistrationList = new ArrayList<>(16);
    private ServletRegistration servletRegistration;
    private ServletContext servletContext;

//    public static final Set<Filter> FILTER_SET = new HashSet<>();
//    public static final AtomicLong SERVLET_TIME = new AtomicLong();
//    public static final AtomicLong FILTER_TIME = new AtomicLong();
//    private long beginTime;
    private int pos;

    protected ServletFilterChain() {
    }

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

        //Initialization request
        if (pos == 0) {
            ServletHttpServletRequest httpServletRequest = ServletUtil.unWrapper(request);
            if (httpServletRequest != null) {
                httpServletRequest.setMultipartConfigElement(servletRegistration.getMultipartConfigElement());
                httpServletRequest.setServletSecurityElement(servletRegistration.getServletSecurityElement());
            }
        }

        //Initialization Servlet
        try {
            if (!servletRegistration.isInitServlet()) {
                synchronized (servletRegistration.getServlet()) {
                    if (!servletRegistration.isInitServlet()) {
                        servletRegistration.getServlet().init(servletRegistration.getServletConfig());
                        if (listenerManager.hasServletRequestListener()) {
                            listenerManager.onServletRequestInitialized(new ServletRequestEvent(servletContext, request));
                        }
                    }
                }
                servletRegistration.setInitServlet(true);
            }
        } catch (Throwable t) {
            String msg = String.format("servlet init fail! cant do filter() and service(). servlet = %s, class = %s, error = %s",
                    servletRegistration.getName(), servletRegistration.getClassName(), t.toString());
            logger.warn(msg, t);
            response.setCharacterEncoding("utf-8");
            response.setContentType("text/html");
            if (response instanceof HttpServletResponse) {
                ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            response.getWriter().write(
                    "<!DOCTYPE html>\n" +
                            "<html lang=\"en\">\n" +
                            "<head>\n" +
                            "    <meta charset=\"UTF-8\">\n" +
                            "    <title>Servlet init fail!</title>\n" +
                            "</head>\n" +
                            "<body>\n" +
                            "<p>" + msg + "</p>\n" +
                            "</body>\n" +
                            "</html>");
            return;
        }

        // do filter() and service()
        if (pos < filterRegistrationList.size()) {
            FilterMapper.Element<ServletFilterRegistration> element = filterRegistrationList.get(pos);
            pos++;
            Filter filter = element.getObject().getFilter();
            filter.doFilter(request, response, this);
        } else {
            try {
                servletRegistration.getServlet().service(request, response);
            } finally {
                if (listenerManager.hasServletRequestListener()) {
                    listenerManager.onServletRequestDestroyed(new ServletRequestEvent(servletContext, request));
                }
            }
        }
    }

    public boolean isFilterEnd() {
        return pos == filterRegistrationList.size();
    }

    public ServletFilterRegistration getFilterRegistration() {
        if (isFilterEnd()) {
            return null;
        }
        return filterRegistrationList.get(pos).getObject();
    }

    public ServletRegistration getServletRegistration() {
        return servletRegistration;
    }

    public List<FilterMapper.Element<ServletFilterRegistration>> getFilterRegistrationList() {
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
