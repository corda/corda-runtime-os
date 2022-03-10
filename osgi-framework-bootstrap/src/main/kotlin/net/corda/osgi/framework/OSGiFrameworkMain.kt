package net.corda.osgi.framework

import net.corda.osgi.framework.OSGiFrameworkMain.Companion.main
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler
import java.nio.file.Files
import java.security.Policy
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * This class provided the main entry point for the applications built with the `corda.common-app` plugin.
 *
 * Modules having in `bundle.gradle` the blocks
 * ```gradle
 * plugins {
 *    id 'corda.common-app'
 * }
 *
 * dependencies {
 *    compileOnly "org.osgi:osgi.annotation:$osgiVersion"
 *    compileOnly "org.osgi:osgi.cmpn:$osgiVersion"
 *    compileOnly "org.osgi:osgi.core:$osgiVersion"
 * }
 * ```
 * result in building a bootable JAR named `corda-<module_name>-<version>.jar` in the `build/bin` directory.
 * The bootable JAR zips the [Apache Felix](https://felix.apache.org/) OSGi framework,
 * the module assembles as an OSGi  bundle and all the OSGi bundles it requires.
 *
 * The bootable JAR is self sufficient to start with `java -jar corda-<module_name>-<version>.jar`.
 * The main entry point of the bootable JAR is the [main] method.
 */
class OSGiFrameworkMain {

    companion object {
        /**
         * Full qualified name of the OSGi framework factory should be part of the class path.
         */
        private const val FRAMEWORK_FACTORY_FQN = "org.apache.felix.framework.FrameworkFactory"

        /**
         * Prefix of the temporary directory used as bundle cache.
         */
        private const val FRAMEWORK_STORAGE_PREFIX = "osgi-cache"

        /**
         * Wait for stop of the OSGi framework, without timeout.
         */
        private const val NO_TIMEOUT = 0L

        /**
         * Location of the list of bundles to install in the [OSGiFrameworkWrap] instance.
         * The location is relative to run time class path:
         * * `build/resources/main` in a gradle project;
         * * the root of the fat executable `.jar`.
         */
        const val APPLICATION_BUNDLES = "application_bundles"

        /**
         * Location of the file listing the extra system packages exposed from the JDK to the framework.
         * See [OSGi Core Release 7 - 4.2.2 Launching Properties](http://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html#framework.lifecycle.launchingproperties)
         * The location is relative to run time class path:
         * * `build/resources/main` in a gradle project;
         * * the root of the fat executable `.jar`.
         */
        @Suppress("MaxLineLength")
        const val SYSTEM_PACKAGES_EXTRA = "system_packages_extra"

        /**
         * The main entry point for the bootable JAR built with the `corda.common-app` plugin.

         * This method bootstraps the application:
         * 1. **Start Up**
         *      1. Start Felix OSGi framework
         *      2. Install OSGi framework services.
         * 2.  **Load bundles in bootstrapper**
         *      1. Install OSGi bundles in the OSGi framework,
         *      2. Activate OSGi bundles.
         * 3. **Call application entry-point**
         *      1. Call the [net.corda.osgi.api.Application.startup] method of active application bundles, if any,
         *      passing [args].
         *
         *  Then, the method waits for the JVM receives the signal to terminate to
         *  1. **Shut Down**
         *      1. Call the [net.corda.osgi.api.Application.shutdown] method of application bundles, if any.
         *      1. Deactivate OSGi bundles.
         *      2. Stop the OSGi framework.
         *
         * @param args passed by the OS when invoking JVM to run this bootable JAR.
         */
        @Suppress("MaxLineLength")
        @JvmStatic
        fun main(args: Array<String>) {
            /**
             * Set the Java security policy programmatically, as required by OSGi Security.
             * @see <a href="https://felix.apache.org/documentation/subprojects/apache-felix-framework-security.html">Felix Framework Security</a>
             */
            Policy.setPolicy(AllPermissionsPolicy())

            /**
             * `java.util.logging` logs directly to the console for Apache Aries and Liquibase (at least),
             *  but we can intercept and redirect that here to use our logger.
             *
             * Add the following logger to log4j2.xml, to (re)enable the Apache Aries messages if you want them,
             * for example
             * ```
             * <Logger name="org.apache.aries.spifly" level="info" additivity="false">-->
             *     <AppenderRef ref="Console-ErrorCode-Appender-Println"/>-->
             * </Logger>
             * ```
             */
            SLF4JBridgeHandler.removeHandlersForRootLogger()
            SLF4JBridgeHandler.install()

            /**
             * Standard exit codes are documented here:
             * @see <a href="https://tldp.org/LDP/abs/html/exitcodes.html">Exit Codes</a>
             */
            var exitCode = 0

            val logger = LoggerFactory.getLogger(OSGiFrameworkMain::class.java)
            try {
                val frameworkStorageDir = Files.createTempDirectory(FRAMEWORK_STORAGE_PREFIX)
                val osgiFrameworkWrap = OSGiFrameworkWrap(
                    OSGiFrameworkWrap.getFrameworkFrom(
                        FRAMEWORK_FACTORY_FQN,
                        frameworkStorageDir,
                        OSGiFrameworkWrap.getFrameworkPropertyFrom(SYSTEM_PACKAGES_EXTRA)
                    )
                )
                try {
                    Runtime.getRuntime().addShutdownHook(thread(name = "shutdown", start = false) {
                        if (OSGiFrameworkWrap.isStoppable(osgiFrameworkWrap.getState())) {
                            osgiFrameworkWrap.stop()
                        }
                    })
                    osgiFrameworkWrap
                        .start()
                        .install(APPLICATION_BUNDLES)
                        .activate()
                        .startApplication(NO_TIMEOUT, args)
                        .waitForStop(NO_TIMEOUT)
                } finally {
                    // If osgiFrameworkWrap stopped because SIGINT/CTRL+C,
                    // this avoids to call stop twice and log warning.
                    if (OSGiFrameworkWrap.isStoppable(osgiFrameworkWrap.getState())) {
                        osgiFrameworkWrap.stop()
                    }
                }
            } catch (e: Exception) {
                logger.error("Error: ${e.message}!", e)
                exitCode = 1
            }

            if (exitCode != 0) {
                exitProcess(exitCode)
            }
        }
    } //~ companion object
}
