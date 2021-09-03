package net.corda.osgi.framework

import net.corda.osgi.framework.OSGiFrameworkMain.main
import org.slf4j.LoggerFactory
import java.nio.file.Files

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
object OSGiFrameworkMain {

    /**
     * Full qualified name of the OSGi framework factory should be part of the class path.
     */
    const val FRAMEWORK_FACTORY_FQN = "org.apache.felix.framework.FrameworkFactory"

    /**
     * Prefix of the temporary directory used as bundle cache.
     */
    const val FRAMEWORK_STORAGE_PREFIX = "osgi-cache"

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
    const val SYSTEM_BUNDLES = "system_bundles"

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
    @JvmStatic
    @Suppress("TooGenericExceptionCaught")
    fun main(args: Array<String>) {
        val logger = LoggerFactory.getLogger(OSGiFrameworkMain::class.java)
        try {
            val frameworkStorageDir = Files.createTempDirectory(FRAMEWORK_STORAGE_PREFIX)
            Runtime.getRuntime().addShutdownHook(Thread {
                frameworkStorageDir
                    .takeIf(Files::exists)
                    ?.let(Files::walk)
                    ?.sorted(Comparator.reverseOrder())
                    ?.forEach(Files::delete)
            })
            val osgiFrameworkWrap = OSGiFrameworkWrap(
                OSGiFrameworkWrap.getFrameworkFrom(
                    FRAMEWORK_FACTORY_FQN,
                    frameworkStorageDir,
                    OSGiFrameworkWrap.getFrameworkPropertyFrom(SYSTEM_PACKAGES_EXTRA)
                )
            )
            try {
                osgiFrameworkWrap
                    .start()
                    .install(SYSTEM_BUNDLES)
                    .activate()
                    .waitForStop(NO_TIMEOUT)
            } catch (e: Exception) {
                logger.error("Error: ${e.message}!", e)
            }
            osgiFrameworkWrap.exitCode?.let(System::exit)
        } catch (e: IllegalArgumentException) {
            logger.error("Error: ${e.message}!", e)
        } catch (e: UnsupportedOperationException) {
            logger.error("Error: ${e.message}!", e)
        } catch (e: SecurityException) {
            logger.error("Error: ${e.message}!", e)
        }
    }
}