package com.github.netty.protocol.servlet.ssl;

import io.netty.handler.ssl.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public class SslContextBuilders {

    public static SslContextBuilder newSslContextBuilderJks(File jksKeyFile, File jksPassword) throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException {
        String password = jksPassword == null ? null : new String(Files.readAllBytes(jksPassword.toPath()));
        return newSslContextBuilderJks(jksKeyFile, password);
    }

    public static SslContextBuilder newSslContextBuilderJks(File jksKeyFile, String jksPassword) throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException {
        char[] password = jksPassword == null ? null : jksPassword.toCharArray();
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(jksKeyFile), password);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        return SslContextBuilder.forServer(keyManagerFactory);
    }

    public static SslContextBuilder newSslContextBuilderPem(File crtFile, File pemFile) {
        return SslContextBuilder.forServer(crtFile, pemFile);
    }

    public static SslContext newSslContext(SslContextBuilder builder, boolean h2) throws SSLException {
        String[] protocols = h2 ? new String[]{ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1}
                : new String[]{ApplicationProtocolNames.HTTP_1_1};
        return builder.sslProvider(SslProvider.isAlpnSupported(SslProvider.OPENSSL) ?
                SslProvider.OPENSSL :
                SslProvider.JDK)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, protocols))
                .build();
    }

}
