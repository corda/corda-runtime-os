package net.corda.osgi.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Policy;
import java.security.URIParameter;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * This class provided the main entry point for the applications built with the {@code corda.common-app} plugin.
 *
 * Modules having in {@code bundle.gradle} the blocks
 * <p/>
 * <pre>{@code
 * plugins {
 *    id 'corda.common-app'
 * }
 *
 * dependencies {
 *    compileOnly platform("net.corda:corda-api:$cordaApiVersion")
 *    compileOnly 'org.osgi:osgi.annotation'
 *    compileOnly 'org.osgi:osgi.core'
 * }
 * }</pre>
 * <p/>
 * result in building a bootable JAR named
 * <pre>{@code
 *     corda-<module_name>-<version>.jar
 * }</pre>
 * in the {@code build/bin} directory.
 * The bootable JAR zips the <a href="https://felix.apache.org/">Apache Felix</a> OSGi framework,
 * the module assembles as an OSGi  bundle and all the OSGi bundles it requires.
 *
 * The bootable JAR is self-sufficient to start with
 * <pre>{@code
 *     java -jar corda-<module_name>-<version>.jar
 * }</pre>
 * The main entry point of the bootable JAR is the {@link #main} method.
 */
final class OSGiFrameworkMain {
    /**
     * Full qualified name of the OSGi framework factory should be part of the class path.
     */
    private static final String FRAMEWORK_FACTORY_FQN = "org.apache.felix.framework.FrameworkFactory";

    /**
     * Prefix of the temporary directory used as bundle cache.
     */
    private static final String FRAMEWORK_STORAGE_PREFIX = "osgi-cache";

    /**
     * Wait for stop of the OSGi framework, without timeout.
     */
    private static final long NO_TIMEOUT = 0L;

    /**
     * Location of the list of bundles to install in the {@link OSGiFrameworkWrap} instance.
     * The location is relative to run time class path:
     * * {@code build/resources/main} in a gradle project;
     * * the root of the fat executable {@code .jar}.
     */
    static final String APPLICATION_BUNDLES = "application_bundles";

    /**
     * Location of the file listing the extra system packages exposed from the JDK to the framework.
     * See <a href="http://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html#framework.lifecycle.launchingproperties">OSGi Core Release 7 - 4.2.2 Launching Properties</a>
     * The location is relative to run time class path:
     * * {@code build/resources/main} in a gradle project;
     * * the root of the fat executable {@code .jar}.
     */
    static final String SYSTEM_PACKAGES_EXTRA = "system_packages_extra";

    /**
     * The main entry point for the bootable JAR built with the `corda.common-app` plugin.
     *
     * This method bootstraps the application:
     * 1. **Start Up**
     *      1. Start Felix OSGi framework
     *      2. Install OSGi framework services.
     * 2.  **Load bundles in bootstrapper**
     *      1. Install OSGi bundles in the OSGi framework,
     *      2. Activate OSGi bundles.
     * 3. **Call application entry-point**
     *      1. Call the {@link net.corda.osgi.api.Application#startup} method of active application bundles, if any,
     *      passing {@code args}.
     *
     *  Then, the method waits for the JVM receives the signal to terminate to
     *  1. **Shut Down**
     *      1. Call the {@link net.corda.osgi.api.Application#shutdown} method of application bundles, if any.
     *      1. Deactivate OSGi bundles.
     *      2. Stop the OSGi framework.
     *
     * @param args passed by the OS when invoking JVM to run this bootable JAR.
     */
    public static void main(String[] args) throws Exception {
        /**
         * Set the Java security policy programmatically, as required by OSGi Security.
         * @see <a href="https://felix.apache.org/documentation/subprojects/apache-felix-framework-security.html">Felix Framework Security</a>
         */
        final URL policy = OSGiFrameworkMain.class.getResource("all-permissions.policy");
        if (policy != null) {
            Policy.setPolicy(Policy.getInstance("JavaPolicy", new URIParameter(policy.toURI())));
        }

        /**
         * {@code java.util.logging} logs directly to the console for Apache Aries and Liquibase (at least),
         *  but we can intercept and redirect that here to use our logger.
         *
         * Add the following logger to log4j2.xml, to (re)enable the Apache Aries messages if you want them,
         * for example
         * <pre>{@code
         * <Logger name="org.apache.aries.spifly" level="info" additivity="false">-->
         *     <AppenderRef ref="Console-ErrorCode-Appender-Println"/>-->
         * </Logger>
         * }</pre>
         */
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        /**
         * Standard exit codes are documented here:
         * @see <a href="https://tldp.org/LDP/abs/html/exitcodes.html">Exit Codes</a>
         */
        int exitCode = 0;

        final Logger logger = LoggerFactory.getLogger(OSGiFrameworkMain.class);
        try {
            final Path frameworkStorageDir = Files.createTempDirectory(FRAMEWORK_STORAGE_PREFIX);
            OSGiFrameworkWrap osgiFrameworkWrap = new OSGiFrameworkWrap(
                OSGiFrameworkWrap.getFrameworkFrom(
                    FRAMEWORK_FACTORY_FQN,
                    frameworkStorageDir,
                    OSGiFrameworkWrap.getFrameworkPropertyFrom(SYSTEM_PACKAGES_EXTRA)
                )
            );
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (OSGiFrameworkWrap.isStoppable(osgiFrameworkWrap.getState())) {
                        osgiFrameworkWrap.stop();
                    }
                }, "shutdown"));

                // WARNING:  comment this and the installFromDirectory line to disable loading of the
                // jdbc driver specified on the command line as -ddatabase.jdbc.directory=/some/where
                final Path driverDirectory = getDbDriverDirectory(args);

                osgiFrameworkWrap
                    .start()
                    .installFromDirectory(driverDirectory)
                    .install(APPLICATION_BUNDLES)
                    .activate()
                    .startApplication(NO_TIMEOUT, args)
                    .waitForStop(NO_TIMEOUT);
            } finally {
                // If osgiFrameworkWrap stopped because SIGINT/CTRL+C,
                // this avoids to call stop twice and log warning.
                if (OSGiFrameworkWrap.isStoppable(osgiFrameworkWrap.getState())) {
                    osgiFrameworkWrap.stop();
                }
            }
        } catch (Exception e) {
            logger.error("Error: " + e.getMessage(), e);
            exitCode = 1;
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Get the db driver path up front, rather than in the {@link net.corda.osgi.api.Application}
     *
     * We need this cli arg *before* the application parses the args
     * as we need it to find the location of the jdbc drivers so that
     * we can install them here.
     */
    private static Path getDbDriverDirectory(String[] args) {
        final List<String> jdbcValues = Arrays.stream(args)
            .filter(a -> a.contains("database.jdbc.directory"))
            .collect(toUnmodifiableList());
        if (jdbcValues.isEmpty()) {
            return null;
        }
        final String jdbcValue = jdbcValues.get(0);
        if (jdbcValue.indexOf('=') == -1) {
            return null;
        }
        final String path = jdbcValue.split("=", 2)[1];
        return Paths.get(path);
    }
}
