package bananatree.jetty;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;
import org.springframework.stereotype.Component;

@Component
public class JettyHttp2Customizer implements EmbeddedServletContainerCustomizer {

    private final ServerProperties serverProperties;

    @Autowired
    public JettyHttp2Customizer(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    @Override
    public void customize(ConfigurableEmbeddedServletContainer container) {
        JettyEmbeddedServletContainerFactory factory = (JettyEmbeddedServletContainerFactory) container;

        factory.addServerCustomizers(new JettyServerCustomizer() {
            @Override
            public void customize(Server server) {
                if (serverProperties.getSsl() != null && serverProperties.getSsl().isEnabled()) {
                    ServerConnector connector = (ServerConnector) server.getConnectors()[0];
                    SslContextFactory sslContextFactory = connector.getConnectionFactory(SslConnectionFactory.class).getSslContextFactory();
                    HttpConfiguration httpConfiguration = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();

                    configureSslContextFactory(sslContextFactory);
                    ConnectionFactory[] connectionFactories = createConnectionFactories(sslContextFactory, httpConfiguration);

                    ServerConnector serverConnector = new ServerConnector(server, connectionFactories);
                    serverConnector.setPort(connector.getPort());
                    // override existing connectors with new ones
                    server.setConnectors(new Connector[]{serverConnector});
                }
            }

            private void configureSslContextFactory(SslContextFactory sslContextFactory) {
                sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
                sslContextFactory.setUseCipherSuitesOrder(true);
            }

            private ConnectionFactory[] createConnectionFactories(SslContextFactory sslContextFactory, HttpConfiguration httpConfiguration) {
                return new ConnectionFactory[] {
                    new SslConnectionFactory(sslContextFactory, "alpn"),
                    new ALPNServerConnectionFactory("h2", "h2-17", "h2-16", "h2-15", "h2-14"),
                    new HTTP2ServerConnectionFactory(httpConfiguration)
                };
            }
        });
    }
}
