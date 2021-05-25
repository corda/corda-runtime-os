package net.corda.sample.goodbye

import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(immediate = true)
class GoodbyeWorld : BundleActivator, Application {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(GoodbyeWorld::class.java)
    }

    init {
        logger.info("INIT")
    }

    @Activate
    override fun start(context: BundleContext?) {
        logger.info("START")
    }

    override fun startup(args: Array<String>) {
        logger.info("START-UP")
        Thread.sleep(1000)
        Thread {
            shutdownOSGiFramework()
        }.start()
    }

    override fun shutdown() {
        logger.info("SHUTDOWN")
    }

    private fun shutdownOSGiFramework() {
        val bundleContext: BundleContext? = FrameworkUtil.getBundle(GoodbyeWorld::class.java).bundleContext
        if (bundleContext != null) {
            val shutdownServiceReference: ServiceReference<Shutdown>? =
                bundleContext.getServiceReference(Shutdown::class.java)
            if (shutdownServiceReference != null) {
                bundleContext.getService(shutdownServiceReference)?.shutdown(bundleContext.bundle)
            }
        }
    }

    @Deactivate
    override fun stop(context: BundleContext?) {
        logger.info("STOP")
    }

}

