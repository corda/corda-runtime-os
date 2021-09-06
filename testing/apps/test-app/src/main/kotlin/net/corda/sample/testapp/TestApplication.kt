package net.corda.sample.testapp

import net.corda.osgi.api.Application
import net.corda.sample.testapp.TestApplication.Companion.SHUTDOWN_DELAY
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/**
 * This class is the entry point of the didactic application showing how to run an *Application* from a bootable JAR.
 *
 * The application shutdowns automatically after [SHUTDOWN_DELAY] ms to allow automatic tests.
 *
 * @param sandboxService    Set automatically because both [SandboxService] and this class are OSGi components.
 */
@Component(immediate = true)
class TestApplication : Application {

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
    override fun run(args: Array<String>) : Int {
        logger.info("Startup(${args.asList()}).")
        logger.info("Press [CTRL+C] to stop the application or wait $SHUTDOWN_DELAY ms before auto shutdown...")
        Thread.sleep(SHUTDOWN_DELAY)
        return 0
    }
}