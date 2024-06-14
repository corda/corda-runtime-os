package net.corda.web.server

import io.javalin.Javalin
import net.corda.utilities.classload.OsgiClassLoader
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.utilities.executeWithStdErrSuppressed
import net.corda.utilities.trace
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object JavalinStarter {
    // JavalinLogger is shared for all Javalin instances and logs their configuration
    //  RestServerInternal sets '/META-INF/resources/' which means if another Javalin instance is started
    //  at the same time, it could be logged by JavalinServer using a different class loader.
    //  This issue is reported as:
    //  JavalinException: Static resource directory with path: '/META-INF/resources/' does not exist`
    //  when another server is started
    // This helper utility ensures we only start one instance at a time
    private val logger = LoggerFactory.getLogger(JavalinStarter::class.java)
    private val serverStartLock = ReentrantLock()

    /**
     * Creates and starts the Javalin server and ensuring only one actioned at a time.
     *
     * @param name Name of the server (to be used in logging only)
     * @param javalinFactory to create the Javalin server object
     * @param port the port to start the server on
     * @param host the host to use (default: localhost)
     * @param threadLocalClassLoaderBundles any bundles to use in the ThreadLocal class loader
     */
    @Suppress("TooGenericExceptionThrown")
    fun startServer(
        name: String,
        javalinFactory: () -> Javalin,
        port: Int,
        host: String? = null,
        threadLocalClassLoaderBundles: List<Bundle> = emptyList(),
    ): Javalin {
        return serverStartLock.withLock {
            val server = javalinFactory()
            val existingSystemErrStream = System.err
            try {
                logger.trace { "Starting the $name Javalin server." }

                val bundle = FrameworkUtil.getBundle(JettyWebSocketServletFactory::class.java)
                if (bundle != null) {
                    val bundleList = threadLocalClassLoaderBundles.plus(bundle)
                    val osgiClassLoader = OsgiClassLoader(bundleList)
                    // We need to set thread context classloader at start time as
                    // `org.eclipse.jetty.websocket.servlet.WebSocketServletFactory.Loader.load` relies on it to perform
                    // classloading during `start` method invocation.
                    executeWithThreadContextClassLoader(osgiClassLoader) {
                        // Required because Javalin prints an error directly to stderr if it cannot find a logging
                        // implementation via standard class loading mechanism. This mechanism is not appropriate for OSGi.
                        // The logging implementation is found correctly in practice.
                        executeWithStdErrSuppressed {
                            host?.let {
                                server.start(it, port)
                                it
                            }?:server.start(port)
                        }
                    }
                } else {
                    host?.let {
                        server.start(it, port)
                        it
                    }?:server.start(port)
                }
                logger.trace { "Starting the $name Javalin server completed." }
            } catch (e: Exception) {
                "Error when starting the $name Javalin server".let {
                    logger.error("$it: ${e.message}")
                    throw Exception(it, e)
                }
            } finally {
                System.setErr(existingSystemErrStream)
            }
            server
        }
    }
}