import org.apache.catalina.*;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.servlet.ServletException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        final Tomcat tomcat = new Tomcat();

        int port = 8080;
        tomcat.setPort(port);

        //
        // Http Connector
        //

        // https://tomcat.apache.org/tomcat-8.0-doc/config/http.html
        // <Connector port="8080" protocol="HTTP/1.1" connectionTimeout="20000" />
        Connector httpConnector = getConnector(
                "HTTP/1.1",
                port,
                "UTF-8",
                true,
                Integer.parseInt(System.getProperty("http.maxThread", "200")));

        // Set attributes (https://tomcat.apache.org/tomcat-8.0-doc/config/http.html)
        // Example:
        //   http.prop.connectionTimeout = 2000
        //   http.prop.bindOnInit = false
        setPropertiesFromSystem(httpConnector, "http.prop.");

        if ("true".equalsIgnoreCase(System.getProperty("http.enableSSL"))) {
            enableSSL(httpConnector, true);
        }

        String httpProxyUrl = System.getProperty("http.proxyUrl");
        if (httpProxyUrl != null && !httpProxyUrl.isEmpty()) {
            setProxy(httpConnector, httpProxyUrl);
        }

        if ("true".equalsIgnoreCase(System.getProperty("http.compression"))) {
            String httpCompressableMimeTypes = System.getProperty("http.compressableMimeTypes");
            enableCompression(httpConnector, httpCompressableMimeTypes);
        }

        addConnector(tomcat, httpConnector);

        //
        // Ajp Connector
        //

        // https://tomcat.apache.org/tomcat-8.0-doc/config/ajp.html
        // <Connector port="8009" protocol="AJP/1.3" redirectPort="8443" />
        Connector ajpConnector = getConnector(
                "AJP/1.3",
                8009,
                "UTF-8",
                true,
                Integer.parseInt(System.getProperty("ajp.maxThread", "200")));

        // https://tomcat.apache.org/tomcat-8.0-doc/config/ajp.html
        setPropertiesFromSystem(ajpConnector, "ajp.prop.");

        String ajpProxyUrl = System.getProperty("ajp.proxyUrl");
        if (ajpProxyUrl != null && !ajpProxyUrl.isEmpty()) {
            setProxy(ajpConnector, ajpProxyUrl);
        }

        addConnector(tomcat, ajpConnector);

        //
        // Context
        //

        Context ctx = addWebappContext(tomcat, "/", "target/classes", "src/main/webapp/");
        setupContext(ctx, true, tomcat.getServer(), false, null, null, 30 /* mins */);

        //
        // Start
        //

        addShutdownHook(tomcat);
        tomcat.start();
        tomcat.getServer().await();
    }

    private static Connector getConnector(
            String protocol,
            int port,
            String uriEncoding,
            boolean useBodyEncodingForURI,
            int maxThreads) throws URISyntaxException {

        final Connector connector = new Connector(protocol);
        connector.setPort(port);

        if (null != uriEncoding) {
            connector.setURIEncoding(uriEncoding);
        }
        connector.setUseBodyEncodingForURI(useBodyEncodingForURI);

        if (maxThreads > 0) {
            ProtocolHandler handler = connector.getProtocolHandler();
            if (handler instanceof AbstractProtocol) {
                ((AbstractProtocol) handler).setMaxThreads(maxThreads);
            } else {
                logger.warn("WARNING: Could not set maxThreads!");
            }
        }

        return connector;
    }

    // This can use in http protocol
    private static void enableSSL(Connector connector, boolean enableClientAuth) {
        connector.setSecure(true);
        connector.setProperty("SSLEnabled", "true");
        connector.setProperty("allowUnsafeLegacyRenegotiation", "false");

        String pathToTrustStore = System.getProperty("javax.net.ssl.trustStore");
        if (pathToTrustStore != null) {
            connector.setProperty("sslProtocol", "tls");
            File truststoreFile = new File(pathToTrustStore);
            connector.setAttribute("truststoreFile", truststoreFile.getAbsolutePath());
            logger.info(truststoreFile.getAbsolutePath());
            connector.setAttribute("trustStorePassword", System.getProperty("javax.net.ssl.trustStorePassword"));
        }

        String pathToKeystore = System.getProperty("javax.net.ssl.keyStore");
        if (pathToKeystore != null) {
            File keystoreFile = new File(pathToKeystore);
            connector.setAttribute("keystoreFile", keystoreFile.getAbsolutePath());
            logger.info(keystoreFile.getAbsolutePath());
            connector.setAttribute("keystorePass", System.getProperty("javax.net.ssl.keyStorePassword"));
        }

        connector.setAttribute("clientAuth", enableClientAuth);
    }

    // This can use in http & ajp protocol
    private static void setProxy(Connector connector, String proxyBaseUrl) throws URISyntaxException {
        URI uri = new URI(proxyBaseUrl);
        String scheme = uri.getScheme();
        connector.setScheme(scheme);
        if (scheme.equals("https") && !connector.getSecure()) {
            connector.setSecure(true);
        }
        if (uri.getPort() > 0) {
            connector.setProxyPort(uri.getPort());
        } else if (scheme.equals("http")) {
            connector.setProxyPort(80);
        } else if (scheme.equals("https")) {
            connector.setProxyPort(443);
        }
    }

    // This can use in http protocol
    private static void enableCompression(Connector connector, String compressableMimeTypes) {
        connector.setProperty("compression", "on");
        if (compressableMimeTypes == null) {
            compressableMimeTypes = "text/html,text/xml,text/plain,text/css,application/json,application/xml," +
                    "text/javascript,application/javascript";
        }

        connector.setProperty("compressableMimeType", compressableMimeTypes);
    }

    private static void setPropertiesFromSystem(Connector connector, String propKeyPrefix) {
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(propKeyPrefix)) {
                String propKey = key.substring(propKeyPrefix.length());
                String propValue = System.getProperty(key);
                if (IntrospectionUtils.setProperty(connector, propKey, propValue)) {
                    logger.info("{} set property {}={}", connector, propKey, propValue);
                } else {
                    logger.warn("{} did not set property {}={}", connector, propKey, propValue);
                }
            }
        }
    }

    private static void addConnector(Tomcat tomcat, Connector connector) {
        final Service service = tomcat.getService();

        // set first connector for main
        if (service.findConnectors().length == 0) {
            tomcat.setConnector(connector);
        }

        service.addConnector(connector);
    }


    /**
     * Stops the embedded Tomcat server.
     */
    private static void addShutdownHook(final Tomcat tomcat) {
        // add shutdown hook to stop server
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    if (tomcat != null) {
                        logger.info("Stop tomcat server");
                        tomcat.getServer().stop();
                    }
                } catch (LifecycleException exception) {
                    throw new RuntimeException("WARNING: Cannot Stop Tomcat " + exception.getMessage(), exception);
                }
            }
        });
    }

    private static Context addWebappContext(final Tomcat tomcat,
                                            final String contextPath,
                                            final String classesPath,
                                            final String webappPath) throws ServletException {
        logger.info("configuring app with basedir: "
                + new File(webappPath).getAbsolutePath());

        StandardContext ctx = (StandardContext) tomcat.addWebapp(
                contextPath.replaceFirst("/$", ""), new File(webappPath).getAbsolutePath());
        ctx.setUnpackWAR(false);

        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(
                resources, "/WEB-INF/classes",
                new File(classesPath).getAbsolutePath(), "/"));

        ctx.setResources(resources);

        return ctx;
    }

    private static void setupContext(Context ctx,
                                     boolean shutdownIfFailure, final Server server,
                                     boolean scanBootstrapClassPath,
                                     String contextXml,
                                     Manager manager,
                                     Integer sessionTimeout) throws MalformedURLException {

        if (shutdownIfFailure) {
            // allow Tomcat to shutdown if a context failure is detected
            if (server instanceof StandardServer) {
                ctx.addLifecycleListener(new LifecycleListener() {
                    public void lifecycleEvent(LifecycleEvent event) {
                        if (event.getLifecycle().getState() == LifecycleState.FAILED) {
                            System.err.println("SEVERE: Context failed in [" + event.getLifecycle().getClass().getName() + "] lifecycle. Allowing Tomcat to shutdown.");
                            ((StandardServer) server).stopAwait();
                        }
                    }
                });
            }
        }

        if (scanBootstrapClassPath) {
            StandardJarScanner scanner = new StandardJarScanner();
            scanner.setScanBootstrapClassPath(true);
            ctx.setJarScanner(scanner);
        }

        // set the context xml location if there is only one war
        if (contextXml != null) {
            logger.info("Using context config: " + contextXml);
            ctx.setConfigFile(new File(contextXml).toURI().toURL());
        }

        // set the session manager
        if (manager != null) {
            ctx.setManager(manager);
        }

        //set the session timeout
        if (sessionTimeout != null) {
            ctx.setSessionTimeout(sessionTimeout);
        }
    }
}
