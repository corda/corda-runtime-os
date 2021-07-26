package net.corda.sample.testapp

import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This class is the entry point of the didactic application showing how to run an *Application* from a bootable JAR.
 *
 * The application shutdowns automatically after [SHUTDOWN_DELAY] ms to allow automatic tests.
 *
 * @param sandboxService    Set automatically because both [SandboxService] and this class are OSGi components.
 */
@Component(immediate = true)
class TestApplication @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutdownService: Shutdown
) : Application {

    private companion object {

        private val logger = contextLogger()

        /**
         * Time in ms before the application shutdown itself.
         */
        private val SHUTDOWN_DELAY = 5000L

    } //~ companion

    /**
     * The `osgi-framework-bootstrap` module calls this method as entry point of the application.
     *
     * This method is called by after the OSGI framework wired all bundles and services.
     *
     * Properties annotated with `@Reference` are already set when this method is called.
     *
     * @param args passed from the OS starting the bootable JAR.
     */
    override fun startup(args: Array<String>) {
        logger.info("Startup(${args.asList()}).")
        logger.info("Press [CTRL+C] to stop the application or wait $SHUTDOWN_DELAY ms before auto shutdown...")
        Thread.sleep(SHUTDOWN_DELAY)
        Thread {
            val bundleContext: BundleContext = FrameworkUtil.getBundle(this.javaClass).bundleContext
            shutdownService.shutdown(bundleContext.bundle)
        }.start()
    }

    /**
     * This method is called when the bootable JAR is requested to terminate by the application itself or the
     * operating system because the JVM terminates.
     *
     * The `osgi-framework-bootstrap` module calls this method before to stop the OSGi framework.
     *
     * *WARNING! Do not call [Shutdown] service from here because it calls this method
     * resulting in an infinite recursive loop.
     *
     * *NOTE. The module `osgi-framework-bootstrap` implements an experimental solution to avoid shutdown loops'.
     */
    override fun shutdown() {
        logger.info("Shutdown.")
    }
}