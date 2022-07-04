package com.github.netty.protocol.servlet.ssl;

import io.netty.handler.ssl.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.stream.Stream;

public class SslContextBuilders {

    public static SslContext newSslContextJks(File jksKeyFile, File jksPassword) throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException {
        char[] password = jksPassword == null ? null : new String(Files.readAllBytes(jksPassword.toPath())).toCharArray();
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(jksKeyFile), password);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        return newSslContext(SslContextBuilder.forServer(keyManagerFactory));
    }

    public static SslContext newSslContextPem(File crtFile, File pemFile) throws SSLException {
        return newSslContext(SslContextBuilder.forServer(crtFile, pemFile));
    }

    private static SslContext newSslContext(SslContextBuilder builder) throws SSLException {
        SslProvider sslProvider = Stream.of(SslProvider.values())
                .filter(SslProvider::isAlpnSupported)
                .findAny()
                .orElseThrow(() -> new SSLProtocolException(
                        "Not found SslProvider. place add maven dependency\n" +
                                "        <dependency>\n" +
                                "            <groupId>io.netty</groupId>\n" +
                                "            <artifactId>netty-tcnative-boringssl-static</artifactId>\n" +
                                "            <version>any version. example = 2.0.53.Final</version>\n" +
                                "            <scope>compile</scope>\n" +
                                "        </dependency>\n"));

        ApplicationProtocolConfig protocolConfig = new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1);
        return builder
                .clientAuth(ClientAuth.NONE)
                .sslProvider(sslProvider)
                .applicationProtocolConfig(protocolConfig)
                .build();
    }

}
