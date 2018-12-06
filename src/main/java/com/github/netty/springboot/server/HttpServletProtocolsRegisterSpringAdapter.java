package com.github.netty.springboot.server;

import com.github.netty.core.util.ApplicationX;
import com.github.netty.core.util.StringUtil;
import com.github.netty.register.HttpServletProtocolsRegister;
import com.github.netty.register.servlet.ServletContext;
import com.github.netty.register.servlet.ServletErrorPage;
import com.github.netty.register.servlet.SessionCompositeServiceImpl;
import com.github.netty.register.servlet.SessionService;
import com.github.netty.springboot.NettyProperties;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.SslStoreProvider;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.annotation.Resource;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * httpServlet协议注册器 （适配spring）
 *
 * @author acer01
 * 2018/11/12/012
 */
public class HttpServletProtocolsRegisterSpringAdapter extends HttpServletProtocolsRegister {

    protected final ApplicationX application;

    public HttpServletProtocolsRegisterSpringAdapter(NettyProperties properties, ServletContext servletContext,
                                                     AbstractServletWebServerFactory configurableWebServer) throws Exception {
        super(properties,
                servletContext,
                configurableWebServer.getSsl() != null && configurableWebServer.getSsl().isEnabled()?
                        SslContextBuilder.forServer(getKeyManagerFactory(configurableWebServer.getSsl(),configurableWebServer.getSslStoreProvider())):null);
        this.application = properties.getApplication();
        initServletContext(servletContext,configurableWebServer,properties);
        if(configurableWebServer.getSsl() != null && configurableWebServer.getSsl().isEnabled()) {
            initSslContext(configurableWebServer);
        }
    }

    /**
     * 初始化servlet上下文
     * @return
     */
    protected ServletContext initServletContext(ServletContext servletContext,AbstractServletWebServerFactory configurableWebServer, NettyProperties properties){
        servletContext.setContextPath(configurableWebServer.getContextPath());
        servletContext.setServerHeader(configurableWebServer.getServerHeader());
        servletContext.setServletContextName(configurableWebServer.getDisplayName());

        //session超时时间
        servletContext.setSessionTimeout((int) configurableWebServer.getSession().getTimeout().getSeconds());
        servletContext.setSessionService(newSessionService(properties,servletContext));
        for (MimeMappings.Mapping mapping :configurableWebServer.getMimeMappings()) {
            servletContext.getMimeMappings().add(mapping.getExtension(),mapping.getMimeType());
        }

        //注册错误页
        for(ErrorPage errorPage : configurableWebServer.getErrorPages()) {
            ServletErrorPage servletErrorPage = new ServletErrorPage(errorPage.getStatusCode(),errorPage.getException(),errorPage.getPath());
            servletContext.getErrorPageManager().add(servletErrorPage);
        }
        return servletContext;
    }

    /**
     * 新建会话服务
     * @return
     */
    protected SessionService newSessionService(NettyProperties properties,ServletContext servletContext){
        //组合会话 (默认本地存储)
        SessionCompositeServiceImpl compositeSessionService = new SessionCompositeServiceImpl();

        //启用session远程存储, 利用RPC
        if(StringUtil.isNotEmpty(properties.getSessionRemoteServerAddress())) {
            String remoteSessionServerAddress = properties.getSessionRemoteServerAddress();
            InetSocketAddress address;
            if(remoteSessionServerAddress.contains(":")){
                String[] addressArr = remoteSessionServerAddress.split(":");
                address = new InetSocketAddress(addressArr[0], Integer.parseInt(addressArr[1]));
            }else {
                address = new InetSocketAddress(remoteSessionServerAddress,80);
            }
            compositeSessionService.enableRemoteRpcSession(address,properties);
        }

        //启用session文件存储
        if(properties.isEnablesLocalFileSession()){
            compositeSessionService.enableLocalFileSession(servletContext.getResourceManager());
        }
        return compositeSessionService;
    }

    /**
     * 初始化 HTTPS的SSL 安全配置
     * @param configurableWebServer
     * @return SSL上下文
     * @throws Exception
     */
    protected SslContextBuilder initSslContext(AbstractServletWebServerFactory configurableWebServer) throws Exception {
        SslContextBuilder builder = getSslContextBuilder();
        Ssl ssl = configurableWebServer.getSsl();
        SslStoreProvider sslStoreProvider = configurableWebServer.getSslStoreProvider();

        builder.trustManager(getTrustManagerFactory(ssl, sslStoreProvider));
        if (ssl.getEnabledProtocols() != null) {
            builder.protocols(ssl.getEnabledProtocols());
        }
        if (ssl.getCiphers() != null) {
            builder.ciphers(Arrays.asList(ssl.getCiphers()));
        }
        if (ssl.getClientAuth() == Ssl.ClientAuth.NEED) {
            builder.clientAuth(ClientAuth.REQUIRE);
        }
        else if (ssl.getClientAuth() == Ssl.ClientAuth.WANT) {
            builder.clientAuth(ClientAuth.OPTIONAL);
        }

        ApplicationProtocolConfig protocolConfig = new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1);
        builder.applicationProtocolConfig(protocolConfig);

        return builder;
    }

    /**
     * 获取信任管理器，用于对安全套接字执行身份验证。
     * @param ssl
     * @param sslStoreProvider
     * @return
     * @throws Exception
     */
    protected TrustManagerFactory getTrustManagerFactory(Ssl ssl,SslStoreProvider sslStoreProvider) throws Exception {
        KeyStore store;
        if (sslStoreProvider != null) {
            store = sslStoreProvider.getTrustStore();
        }else {
            store = loadKeyStore(ssl.getTrustStoreType(), ssl.getTrustStoreProvider(),ssl.getTrustStore(), ssl.getTrustStorePassword());
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(store);
        return trustManagerFactory;
    }

    /**
     * 获取密匙管理器
     * @param ssl
     * @param sslStoreProvider
     * @return
     * @throws Exception
     */
    private static KeyManagerFactory getKeyManagerFactory(Ssl ssl,SslStoreProvider sslStoreProvider) throws Exception {
        KeyStore keyStore;
        if (sslStoreProvider != null) {
            keyStore = sslStoreProvider.getKeyStore();
        }else {
            keyStore = loadKeyStore(ssl.getKeyStoreType(), ssl.getKeyStoreProvider(),ssl.getKeyStore(), ssl.getKeyStorePassword());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        char[] keyPassword = (ssl.getKeyPassword() != null) ? ssl.getKeyPassword().toCharArray() : null;
        if (keyPassword == null && ssl.getKeyStorePassword() != null) {
            keyPassword = ssl.getKeyStorePassword().toCharArray();
        }
        keyManagerFactory.init(keyStore, keyPassword);
        return keyManagerFactory;
    }

    /**
     * 加载密匙
     * @param type
     * @param provider
     * @param resource
     * @param password
     * @return
     * @throws Exception
     */
    private static KeyStore loadKeyStore(String type, String provider, String resource,String password) throws Exception {
        if (resource == null) {
            return null;
        }
        type = (type != null) ? type : "JKS";
        KeyStore store = (provider != null) ? KeyStore.getInstance(type, provider) : KeyStore.getInstance(type);
        URL url = ResourceUtils.getURL(resource);
        store.load(url.openStream(), (password == null) ? null : password.toCharArray());
        return store;
    }

    @Override
    public void onServerStart() throws Exception {
        super.onServerStart();

        //注入到spring对象里
        initApplication();
    }

    /**
     *  注入到spring对象里
     */
    protected void initApplication(){
        ApplicationX application = this.application;
        ServletContext servletContext = getServletContext();

        application.addInjectAnnotation(Autowired.class, Resource.class);
        application.addInstance(servletContext.getSessionService());
        application.addInstance(servletContext);
        WebApplicationContext springApplication = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);
        String[] beans = springApplication.getBeanDefinitionNames();
        for (String beanName : beans) {
            Object bean = springApplication.getBean(beanName);
            application.addInstance(beanName,bean,false);
        }
        application.scanner("com.github.netty").inject();
    }

}
