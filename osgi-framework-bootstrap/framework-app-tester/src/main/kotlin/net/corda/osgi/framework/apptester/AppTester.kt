package net.corda.osgi.framework.apptester

import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.slf4j.Logger

class AppTester: Application, BundleActivator {

    private companion object {
        private val logger: Logger = contextLogger()
    }

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
        shutdownOSGiFramework()
    }

    private fun shutdownOSGiFramework() {
        val bundleContext: BundleContext? = FrameworkUtil.getBundle(this.javaClass).bundleContext
        if (bundleContext != null) {
            val shutdownServiceReference: ServiceReference<Shutdown>? =
                bundleContext.getServiceReference(Shutdown::class.java)
            if (shutdownServiceReference != null) {
                bundleContext.getService(shutdownServiceReference)?.shutdown(bundleContext.bundle)
            }
        }
    }

    override fun stop(context: BundleContext?) {
        logger.info("STOP")
    }
}