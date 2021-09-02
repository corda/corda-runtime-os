package net.corda.sample.goodbye

import co.paralleluniverse.fibers.instrument.JavaAgent
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(immediate = true)
class GoodbyeWorld @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown
) : BundleActivator, Application {

    private companion object {
        private val logger: Logger = contextLogger()
    }

    init {
        logger.info("INIT")
    }

    @Activate
    override fun start(context: BundleContext?) {
        logger.info("START")
        logger.info("${JavaAgent::class.qualifiedName} is ${if(JavaAgent.isActive()) "" else "not "}active")
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
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    @Deactivate
    override fun stop(context: BundleContext?) {
        logger.info("STOP")
    }

}

