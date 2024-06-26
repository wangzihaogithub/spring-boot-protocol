package com.github.netty.springboot;

import com.github.netty.core.util.IOUtil;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.SslStoreProvider;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.security.KeyStore;
import java.util.Arrays;

public class SpringUtil {

    public static <T> T getBean(BeanFactory beanFactory, Class<T> requiredType) {
        try {
            return beanFactory.getBean(requiredType);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    public static boolean isSingletonBean(BeanFactory beanFactory, String beanName) {
        if (beanFactory instanceof DefaultSingletonBeanRegistry
                && ((DefaultSingletonBeanRegistry) beanFactory).isSingletonCurrentlyInCreation(beanName)) {
            return true;
        }
        if (beanFactory instanceof ConfigurableBeanFactory &&
                ((ConfigurableBeanFactory) beanFactory).isCurrentlyInCreation(beanName)) {
            return false;
        }
        return beanFactory.containsBean(beanName) && beanFactory.isSingleton(beanName);
    }

    public static Number getNumberBytes(Object object, String methodName) throws InvocationTargetException, IllegalAccessException {
        Object value = ClassUtils.getMethod(object.getClass(), methodName).invoke(object);
        if (!(value instanceof Number)) {
            value = ClassUtils.getMethod(value.getClass(), "toBytes").invoke(value);
        }
        return (Number) value;
    }

    public static SslContextBuilder newSslContext(Ssl ssl, SslStoreProvider sslStoreProvider) {
        SslContextBuilder builder = SslContextBuilder.forServer(getKeyManagerFactory(ssl, sslStoreProvider))
                .trustManager(getTrustManagerFactory(ssl, sslStoreProvider));
        if (ssl.getEnabledProtocols() != null) {
            builder.protocols(ssl.getEnabledProtocols());
        }
        if (ssl.getCiphers() != null) {
            builder.ciphers(Arrays.asList(ssl.getCiphers()));
        }
        if (ssl.getClientAuth() == Ssl.ClientAuth.NEED) {
            builder.clientAuth(ClientAuth.REQUIRE);
        } else if (ssl.getClientAuth() == Ssl.ClientAuth.WANT) {
            builder.clientAuth(ClientAuth.OPTIONAL);
        }
        return builder;
    }

    private static KeyManagerFactory getKeyManagerFactory(Ssl ssl, SslStoreProvider sslStoreProvider) {
        try {
            KeyStore keyStore = getKeyStore(ssl, sslStoreProvider);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            char[] keyPassword = (ssl.getKeyPassword() != null) ? ssl.getKeyPassword().toCharArray() : null;
            if (keyPassword == null && ssl.getKeyStorePassword() != null) {
                keyPassword = getPassword(ssl.getKeyStorePassword()).toCharArray();
            }
            keyManagerFactory.init(keyStore, keyPassword);
            return keyManagerFactory;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String getPassword(String password) {
        if (password == null || password.isEmpty()) {
            return password;
        }
        try {
            URL url = ResourceUtils.getURL(password);
            return IOUtil.readInput(url.openStream(), "UTF-8");
        } catch (Exception e) {
            return password;
        }
    }

    private static KeyStore getKeyStore(Ssl ssl, SslStoreProvider sslStoreProvider) throws Exception {
        if (sslStoreProvider != null) {
            return sslStoreProvider.getKeyStore();
        }
        return loadKeyStore(ssl.getKeyStoreType(), ssl.getKeyStoreProvider(), ssl.getKeyStore(),
                getPassword(ssl.getKeyStorePassword()));
    }

    private static TrustManagerFactory getTrustManagerFactory(Ssl ssl, SslStoreProvider sslStoreProvider) {
        try {
            KeyStore store = getTrustStore(ssl, sslStoreProvider);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(store);
            return trustManagerFactory;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyStore getTrustStore(Ssl ssl, SslStoreProvider sslStoreProvider) throws Exception {
        if (sslStoreProvider != null) {
            return sslStoreProvider.getTrustStore();
        }
        return loadTrustStore(ssl.getTrustStoreType(), ssl.getTrustStoreProvider(), ssl.getTrustStore(),
                ssl.getTrustStorePassword());
    }

    private static KeyStore loadKeyStore(String type, String provider, String resource, String password) throws Exception {
        return loadStore(type, provider, resource, password);
    }

    private static KeyStore loadTrustStore(String type, String provider, String resource, String password) throws Exception {
        if (resource == null) {
            return null;
        }
        return loadStore(type, provider, resource, password);
    }

    private static KeyStore loadStore(String type, String provider, String resource, String password) throws Exception {
        type = (type != null) ? type : "JKS";
        KeyStore store = (provider != null) ? KeyStore.getInstance(type, provider) : KeyStore.getInstance(type);
        try {
            URL url = ResourceUtils.getURL(resource);
            store.load(url.openStream(), (password != null) ? password.toCharArray() : null);
            return store;
        } catch (Exception ex) {
            throw new WebServerException("Could not load key store '" + resource + "'", ex);
        }
    }

}
