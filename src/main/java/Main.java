import org.apache.catalina.*;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.servlet.ServletException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public class Main {

    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        final Tomcat tomcat = new Tomcat();

        //The port that we should run on can be set into an environment variable
        //Look for that variable and default to 8080 if it isn't there.
        String webPort = System.getenv("PORT");
        if(webPort == null || webPort.isEmpty()) {
            webPort = "8080";
        }
        int port = Integer.valueOf(webPort);

        tomcat.setPort(port);

        // https://tomcat.apache.org/tomcat-8.0-doc/config/http.html
        // <Connector port="8080" protocol="HTTP/1.1" connectionTimeout="20000" />
        addConnector(tomcat, getConnector(
                "HTTP/1.1",
                port,
                20000,
                "UTF-8",
                true,
                true,
                200));

        // enableSSL(connector, enableClientAuth);
        // setProxy(connector, proxyBaseUrl);
        // enableCompression(connector, compressableMimeTypes);

        // https://tomcat.apache.org/tomcat-8.0-doc/config/ajp.html
        // <Connector port="8009" protocol="AJP/1.3" redirectPort="8443" />
        addConnector(tomcat, getConnector(
                "AJP/1.3",
                8009,
                20000,
                "UTF-8",
                true,
                true,
                200));

        Context ctx = addWebappContext(tomcat, "/", "target/classes", "src/main/webapp/");
        setupContext(ctx, true, tomcat.getServer(), false, null, null, 30 /* mins */);

        addShutdownHook(tomcat);
        tomcat.start();
        tomcat.getServer().await();
    }

    private static Connector getConnector(
            String protocol,
            int port,
            int connectionTimeout,
            String uriEncoding,
            boolean useBodyEncodingForURI,
            boolean bindOnInit,
            int maxThreads) throws URISyntaxException {

        final Connector connector = new Connector(protocol);
        connector.setPort(port);

        connector.setProperty("connectionTimeout", String.valueOf(connectionTimeout));

        if (null != uriEncoding) {
            connector.setURIEncoding(uriEncoding);
        }
        connector.setUseBodyEncodingForURI(useBodyEncodingForURI);

        if (!bindOnInit) {
            connector.setProperty("bindOnInit", "false");
        }

        if (maxThreads > 0) {
            ProtocolHandler handler = connector.getProtocolHandler();
            if (handler instanceof AbstractProtocol) {
                ((AbstractProtocol) handler).setMaxThreads(maxThreads);
            } else {
                System.out.println("WARNING: Could not set maxThreads!");
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
            System.out.println(truststoreFile.getAbsolutePath());
            connector.setAttribute("trustStorePassword", System.getProperty("javax.net.ssl.trustStorePassword"));
        }

        String pathToKeystore = System.getProperty("javax.net.ssl.keyStore");
        if (pathToKeystore != null) {
            File keystoreFile = new File(pathToKeystore);
            connector.setAttribute("keystoreFile", keystoreFile.getAbsolutePath());
            System.out.println(keystoreFile.getAbsolutePath());
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

    private static void addConnector(Tomcat tomcat, Connector connector) {
        final Service service = tomcat.getService();

        // １つ目のConnectorはtomcatのメインとして設定
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
                        System.out.println("Stop tomcat server");
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
        System.out.println("configuring app with basedir: "
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
            System.out.println("Using context config: " + contextXml);
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
