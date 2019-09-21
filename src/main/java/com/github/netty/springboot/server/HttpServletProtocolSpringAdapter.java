package com.github.netty.springboot.server;

import com.github.netty.core.util.ApplicationX;
import com.github.netty.core.util.StringUtil;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.servlet.ServletContext;
import com.github.netty.protocol.servlet.ServletErrorPage;
import com.github.netty.protocol.servlet.SessionCompositeServiceImpl;
import com.github.netty.protocol.servlet.SessionService;
import com.github.netty.springboot.NettyProperties;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.SslStoreProvider;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;

import javax.annotation.Resource;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * HttpServlet protocol registry (spring adapter)
 * @author wangzihao
 * 2018/11/12/012
 */
public class HttpServletProtocolSpringAdapter extends HttpServletProtocol implements BeanPostProcessor {
    private NettyProperties properties;
    private ApplicationX application;
    @Autowired
    private ListableBeanFactory listableBeanFactory;

    public HttpServletProtocolSpringAdapter(NettyProperties properties, Supplier<Executor> serverHandlerExecutor,ClassLoader classLoader) {
        super(serverHandlerExecutor,new ServletContext(classLoader == null? ClassUtils.getDefaultClassLoader():classLoader));
        this.properties = properties;
        this.application = properties.getApplication();
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        application.addInstance(beanName,bean,false);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if(bean instanceof AbstractServletWebServerFactory && ((AbstractServletWebServerFactory) bean).getPort() > 0){
            try {
                configurableServletContext((AbstractServletWebServerFactory) bean);
            } catch (Exception e) {
                BeanInitializationException exception = new BeanInitializationException(e.getMessage(),e);
                exception.setStackTrace(e.getStackTrace());
                throw exception;
            }
        }
        return bean;
    }

    @Override
    public void onServerStart() throws Exception {
        super.onServerStart();

        //Injection into the spring object
        application.addInjectAnnotation(Autowired.class, Resource.class);
        ServletContext servletContext = getServletContext();
        application.addInstance(servletContext);
        application.addInstance(servletContext.getSessionService());

        if(listableBeanFactory != null) {
            for (String beanName : listableBeanFactory.getBeanDefinitionNames()) {
                Object bean = listableBeanFactory.getBean(beanName);
                application.addInstance(beanName, bean, false);
            }
        }
        application.scanner("com.github.netty").inject();
    }

    protected void configurableServletContext(AbstractServletWebServerFactory configurableWebServer) throws Exception {
        ServletContext servletContext = getServletContext();
        InetSocketAddress address = NettyTcpServerFactory.getServerSocketAddress(configurableWebServer.getAddress(),configurableWebServer.getPort());
        //Server port
        servletContext.setServerAddress(address);
        servletContext.setDocBase(configurableWebServer.getDocumentRoot().getAbsolutePath());
        servletContext.setContextPath(configurableWebServer.getContextPath());
        servletContext.setServerHeader(configurableWebServer.getServerHeader());
        servletContext.setServletContextName(configurableWebServer.getDisplayName());
        servletContext.setResponseWriterChunkMaxHeapByteLength(properties.getHttpServlet().getResponseWriterChunkMaxHeapByteLength());
        //Session timeout
        servletContext.setSessionTimeout((int) configurableWebServer.getSession().getTimeout().getSeconds());
        servletContext.setSessionService(newSessionService(properties,servletContext));
        for (MimeMappings.Mapping mapping :configurableWebServer.getMimeMappings()) {
            servletContext.getMimeMappings().add(mapping.getExtension(),mapping.getMimeType());
        }

        super.setEnableContentCompression(configurableWebServer.getCompression().getEnabled());
        super.setContentSizeThreshold((int) configurableWebServer.getCompression().getMinResponseSize().toBytes());
        super.setCompressionMimeTypes(configurableWebServer.getCompression().getMimeTypes().clone());
        super.setCompressionExcludedUserAgents(configurableWebServer.getCompression().getExcludedUserAgents());

        //Error page
        for(ErrorPage errorPage : configurableWebServer.getErrorPages()) {
            ServletErrorPage servletErrorPage = new ServletErrorPage(errorPage.getStatusCode(),errorPage.getException(),errorPage.getPath());
            servletContext.getErrorPageManager().add(servletErrorPage);
        }

        Ssl ssl = configurableWebServer.getSsl();
        if(ssl != null && ssl.isEnabled()){
            SslStoreProvider sslStoreProvider = configurableWebServer.getSslStoreProvider();
            KeyManagerFactory keyManagerFactory = getKeyManagerFactory(ssl,sslStoreProvider);
            SslContextBuilder sslContextBuilder = getSslContext(keyManagerFactory,ssl,sslStoreProvider);
            super.setSslContextBuilder(sslContextBuilder);
        }
    }

    /**
     * New session service
     * @param properties properties
     * @param servletContext servletContext
     * @return SessionService
     */
    protected SessionService newSessionService(NettyProperties properties,ServletContext servletContext){
        //Composite session (default local storage)
        SessionCompositeServiceImpl compositeSessionService = new SessionCompositeServiceImpl();

        if(StringUtil.isNotEmpty(properties.getHttpServlet().getSessionRemoteServerAddress())) {
            //Enable session remote storage using RPC
            String remoteSessionServerAddress = properties.getHttpServlet().getSessionRemoteServerAddress();
            InetSocketAddress address;
            if(remoteSessionServerAddress.contains(":")){
                String[] addressArr = remoteSessionServerAddress.split(":");
                address = new InetSocketAddress(addressArr[0], Integer.parseInt(addressArr[1]));
            }else {
                address = new InetSocketAddress(remoteSessionServerAddress,80);
            }
            compositeSessionService.enableRemoteRpcSession(address,
                    100,
                    1,
                    true,properties.getNrpc().isClientEnableHeartLog(),
                    properties.getNrpc().getClientHeartInterval());

        }else if(properties.getHttpServlet().isEnablesLocalFileSession()){
            //Enable session file storage
            compositeSessionService.enableLocalFileSession(servletContext.getResourceManager());
        }
        return compositeSessionService;
    }

    /**
     * Initialize the SSL security configuration for HTTPS
     * @param keyManagerFactory keyManagerFactory
     * @param ssl ssl
     * @param sslStoreProvider sslStoreProvider
     * @return The SSL context builder
     * @throws Exception Exception
     */
    protected SslContextBuilder getSslContext(KeyManagerFactory keyManagerFactory, Ssl ssl, SslStoreProvider sslStoreProvider) throws Exception {
        SslContextBuilder builder = SslContextBuilder.forServer(keyManagerFactory);
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
     * Gets a trust manager used to authenticate secure sockets.
     * @param ssl ssl
     * @param sslStoreProvider sslStoreProvider
     * @return TrustManagerFactory
     * @throws Exception Exception
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
     * Get the key manager
     * @param ssl ssl
     * @param sslStoreProvider sslStoreProvider
     * @return KeyManagerFactory
     * @throws Exception Exception
     */
    protected KeyManagerFactory getKeyManagerFactory(Ssl ssl,SslStoreProvider sslStoreProvider) throws Exception {
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
     * Load key
     * @param type type
     * @param provider provider
     * @param resource resource
     * @param password password
     * @return KeyStore
     * @throws Exception Exception
     */
    protected KeyStore loadKeyStore(String type, String provider, String resource,String password) throws Exception {
        if (resource == null) {
            return null;
        }
        type = (type != null) ? type : "JKS";
        KeyStore store = (provider != null) ? KeyStore.getInstance(type, provider) : KeyStore.getInstance(type);
        URL url = ResourceUtils.getURL(resource);
        store.load(url.openStream(), (password == null) ? null : password.toCharArray());
        return store;
    }

}
