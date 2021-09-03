package net.corda.osgi.application

import net.corda.osgi.api.Application
import net.corda.osgi.api.FrameworkService
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(immediate = true)
class ApplicationRunner @Activate constructor(
    @Reference private val application : Application
) {
    companion object {
        private val log = LoggerFactory.getLogger(ApplicationRunner::class.java)
    }

    init {
        log.info("Constructing ${javaClass.name}")
    }

    fun activate(bundleContext : BundleContext) {
        log.info("Activating ${javaClass.name}")
        val ref = bundleContext.getServiceReference(FrameworkService::class.java)
        val frameworkService = bundleContext.getService(ref)
        try {
            frameworkService.setExitCode(application.run(frameworkService.getArgs()))
        } finally {
            bundleContext.getBundle(0).stop()
        }
    }
}