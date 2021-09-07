package net.corda.osgi.application

import net.corda.osgi.api.Application
import net.corda.osgi.api.ExitCode
import net.corda.osgi.api.FrameworkService
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.lang.Exception

@Component(immediate = true)
class ApplicationRunner @Activate constructor(@Reference private val application : Application) {
    @Suppress("TooGenericExceptionCaught")
    fun activate(bundleContext : BundleContext) {
        val log = LoggerFactory.getLogger(ApplicationRunner::class.java)
        val ref = bundleContext.getServiceReference(FrameworkService::class.java)
        val frameworkService = bundleContext.getService(ref)
        try {
            frameworkService.setExitCode(application.run(frameworkService.getArgs()))
        } catch(ex : Exception) {
            log.error(ex.message, ex)
            frameworkService.setExitCode(ExitCode.ERROR)
        } finally {
            bundleContext.getBundle(0).stop()
        }
    }
}