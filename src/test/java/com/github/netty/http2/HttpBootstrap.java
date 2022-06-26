package com.github.netty.http2;

import com.github.netty.StartupServer;
import com.github.netty.javaxservlet.example.MyHttpServlet;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.servlet.ServletContext;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.stream.Stream;

public class HttpBootstrap {

    public static void main(String[] args) throws CertificateException, SSLException {
        StartupServer server = new StartupServer(80);
        server.addProtocol(newHttpProtocol());
        server.start();
    }

    private static HttpServletProtocol newHttpProtocol() throws CertificateException, SSLException {
        ServletContext servletContext = new ServletContext();
        servletContext.setDocBase(System.getProperty("user.dir"), "/webapp");
        servletContext.addServlet("myHttpServlet", new MyHttpServlet())
                .addMapping("/test");
        HttpServletProtocol protocol = new HttpServletProtocol(servletContext);

//        protocol.setSslContext(newSslContext()); 可以选择不用ssl, 直接用http2
        return protocol;
    }

    private static SslContext newSslContext() throws CertificateException, SSLException {
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

        SelfSignedCertificate ssc = new SelfSignedCertificate();
        return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslProvider)
                /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                 * Please refer to the HTTP/2 specification for cipher requirements. */
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();
    }
}
