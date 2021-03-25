package net.corda.osgi.framework

import net.corda.osgi.framework.api.ArgsService
import org.osgi.framework.Bundle
import org.osgi.framework.BundleException
import org.osgi.framework.Constants
import org.osgi.framework.FrameworkEvent
import org.osgi.framework.launch.Framework
import org.osgi.framework.launch.FrameworkFactory
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.util.*

/*
TODO
 * Some techniques to investigate produced logs is what expected.
 * I prefer to throw exception and break execution than just log error, it simplifies tests.
 * I prefer to catch exception above and log and stop the framework.
 * Implement a service to let code in OSGi bundles to ask this OSGI wrapper to shutdown gracefully.
 * Define those parameters - like log directory and osgi cache directory - should be configured as CLi args.
 */

class OSGiFrameworkWrap(
    private val uuid: UUID,             // Used to distinguish different objects in parallel tests.
    internal val framework: Framework,  // Defined `internal` to handle it from unit tests.
) : AutoCloseable {

    companion object {

        private val logger = LoggerFactory.getLogger(OSGiFrameworkWrap::class.java)

        private val bundleStateMap = mapOf(
            // 0x00000020 = 0010.0000 binary
            Bundle.ACTIVE to "active",
            // 0x00000002 = 0000.0010 binary
            Bundle.INSTALLED to "installed",
            // 0x00000004 = 0000.0100 binary
            Bundle.RESOLVED to "resolved",
            // 0x00000008 = 0000.1000 binary
            Bundle.STARTING to "starting",
            // 0x00000010 = 0001.0000 binary
            Bundle.STOPPING to "stopping",
            // 0x00000001 = 0000.0001 binary
            Bundle.UNINSTALLED to "uninstalled"
        )

        /**
         * Extension used to identify `jar` files to [install].
         */
        private const val JAR_EXTENSION = ".jar"


        fun isStartable(status: Int): Boolean {
            val state = status and 0xff
            return state > Bundle.UNINSTALLED && state < Bundle.STOPPING
        }

        fun isStoppable(status: Int): Boolean {
            val state = status and 0xff
            return state > Bundle.STARTING && state <= Bundle.ACTIVE
        }

        @Throws(
            ClassNotFoundException::class,
            SecurityException::class
        )
        fun getFrameworkFrom(
            frameworkFactoryFQN: String,
            frameworkStorageDir: Path
        ): Framework {
            logger.debug("OSGi framework factory = $frameworkFactoryFQN.")
            val frameworkFactory = Class.forName(
                frameworkFactoryFQN,
                true,
                OSGiFrameworkWrap::class.java.classLoader
            ).getDeclaredConstructor().newInstance() as FrameworkFactory
            val configurationMap = mapOf(
                Constants.FRAMEWORK_STORAGE to frameworkStorageDir.toString(),
                Constants.FRAMEWORK_STORAGE_CLEAN to Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT,
            )
            if (logger.isDebugEnabled) {
                configurationMap.forEach { (key, value) -> logger.debug("OSGi property $key = $value.") }
            }
            return frameworkFactory.newFramework(configurationMap)
        }

    }

    @Suppress("MaxLineLength")
    @Throws(
        BundleException::class
    )
    fun activate(): OSGiFrameworkWrap {
        val sortedBundles = bundleMap.values.sortedBy { it.symbolicName }
        sortedBundles.forEach { bundle: Bundle ->
            if (isFragment(bundle)) {
                logger.info("OSGi bundle ${bundle.location} ID = ${bundle.bundleId} ${bundle.symbolicName ?: "\b"} ${bundle.version} ${bundleStateMap[bundle.state]} fragment.")
            } else {
                bundle.start()
            }
        }
        return this
    }

    /**
     * Map of the bundle installed.
     * Synchronized.
     * @see [activate]
     * @see [install]
     */
    private val bundleMap = mutableMapOf<Long, Bundle>()

    fun getUUID(): UUID {
        return uuid
    }

    /**
     * Return 'true' if the 'bundle' is an
     * OSGi [fragment](https://www.osgi.org/developer/white-papers/semantic-versioning/bundles-and-fragments/).
     * OSGi fragments are not subject to activation.
     * @param bundle to check if it is fragment.
     * @return Return 'true' if the 'bundle' is an OSGi fragment.
     */
    private fun isFragment(bundle: Bundle): Boolean {
        return bundle.headers[Constants.FRAGMENT_HOST] != null
    }

    @Throws(
        BundleException::class,
        IllegalStateException::class,
        IOException::class
    )
    fun install(resource: String): OSGiFrameworkWrap {
        val contextClassLoader = Thread.currentThread().contextClassLoader
        if (resource.endsWith(JAR_EXTENSION)) {
            installBundleJar(resource, contextClassLoader)
        } else {
            installBundleList(resource, contextClassLoader)
        }
        return this
    }

    @Throws(
        BundleException::class,
        IllegalStateException::class,
        IOException::class
    )
    private fun installBundleJar(resource: String, classLoader: ClassLoader) {
        logger.debug("OSGi bundle $resource installing...")
        classLoader.getResourceAsStream(resource).use { inputStream ->
            if (inputStream != null) {
                val bundleContext = framework.bundleContext
                    ?: throw IllegalStateException("OSGi framework not active yet.")
                val bundle = bundleContext.installBundle(resource, inputStream)
                bundleMap[bundle.bundleId] = bundle
                logger.debug("OSGi bundle $resource installed.")
            } else {
                throw IOException("OSGi bundle at $resource not found")
            }
        }
    }

    @Throws(
        BundleException::class,
        IllegalStateException::class,
        IOException::class
    )
    private fun installBundleList(resource: String, classLoader: ClassLoader) {
        classLoader.getResourceAsStream(resource).use { inputStream ->
            if (inputStream != null) {
                logger.info("OSGi bundle list at $resource loading...")
                inputStream.bufferedReader().useLines { lines ->
                    lines.map { line -> line.substringBefore('#') }
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .toList()
                        .forEach(::install)
                }
                logger.info("OSGi bundle list at $resource loaded.")
            } else {
                throw IOException("OSGi bundle list at $resource not found")
            }
        }
    }

    @Throws(
        IllegalStateException::class,
    )
    fun setArguments(args: Array<String>): OSGiFrameworkWrap {
        val bundleContext = framework.bundleContext ?: throw IllegalStateException("OSGi framework not active yet.")
        bundleContext.registerService(ArgsService::class.java, ArgsService { args }, Hashtable<String, Any>())
        return this
    }

    @Suppress("MaxLineLength")
    @Throws(
        BundleException::class,
        IllegalStateException::class,
        SecurityException::class
    )
    @Synchronized
    fun start(): OSGiFrameworkWrap {
        if (isStartable(framework.state)) {
            framework.start()
            framework.bundleContext.addBundleListener { bundleEvent ->
                val bundle = bundleEvent.bundle
                logger.info("OSGi bundle ${bundle.location} ID = ${bundle.bundleId} ${bundle.symbolicName ?: "\b"} ${bundle.version} ${bundleStateMap[bundle.state]}.")
            }
            logger.info("OSGi framework ${framework::class.java.canonicalName} ${framework.version} started.")
        } else {
            logger.warn("OSGi framework ${framework::class.java.canonicalName} ${bundleStateMap[framework.state]}.")
        }
        return this
    }

    @Suppress("ForbiddenComment")
    @Throws(
        BundleException::class,
        ClassNotFoundException::class,
        SecurityException::class
    )
    @Synchronized
    fun stop(): OSGiFrameworkWrap {
        if (isStoppable(framework.state)) {
            logger.debug("OSGi framework stop...")
            // todo: investigate stop sequence: is it better to enforce according the activation order?
            framework.stop()
            logger.info("OSGi framework ${framework::class.java.canonicalName} ${framework.version} stop.")
        } else {
            logger.warn("OSGi framework ${framework::class.java.canonicalName} ${bundleStateMap[framework.state]}.")
        }
        return this
    }

    fun waitForStop(timeout: Long): FrameworkEvent {
        framework.waitForStop(timeout).let { frameworkEvent ->
            when (frameworkEvent.type) {
                FrameworkEvent.ERROR -> {
                    logger.error(
                        "OSGi framework stop error: ${frameworkEvent.throwable.message}!",
                        frameworkEvent.throwable
                    )
                }
                FrameworkEvent.STOPPED -> {
                    logger.debug("OSGi framework stop.")
                }
                FrameworkEvent.WAIT_TIMEDOUT -> {
                    logger.warn("OSGi framework stop time out!")
                }
                else -> {
                    logger.error("OSGi framework stop: unknown event type ${frameworkEvent.type}!")
                }
            }
            return frameworkEvent
        }
    }

    // AutoCloseable

    override fun close() {
        stop()
    }


}