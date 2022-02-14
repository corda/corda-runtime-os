package net.corda.virtualnode.manager.test

import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.testing.sandboxes.CpiLoaderService
import net.corda.v5.application.flows.Flow
import net.corda.virtualnode.manager.api.RuntimeRegistration
import org.junit.jupiter.api.fail
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ IntegrationTestService::class ])
class IntegrationTestService @Activate constructor(
    @Reference
    private val loader: CpiLoaderService,
    @Reference
    private val sandboxCreationService: SandboxCreationService,
    @Reference
    private val registration: RuntimeRegistration
) {
    fun loadCPIFromResource(resourceName: String): CPI {
        return loader.loadCPI(resourceName)
    }

    fun unloadCPI(cpi: CPI) {
        loader.unloadCPI(cpi)
    }

    fun createSandboxGroupFor(cpks: Set<CPK>): SandboxGroup =
        sandboxCreationService.createSandboxGroup(cpks)

    fun registerCrypto(sandboxGroup: SandboxGroup) = registration.register(sandboxGroup)

    fun unloadSandboxGroup(sandboxGroup: SandboxGroup) {
        registration.unregister(sandboxGroup)
        // Unload from OSGi and the process.
        sandboxCreationService.unloadSandboxGroup(sandboxGroup)
    }

    fun <T : Any> runFlow(className: String, group: SandboxGroup): T {
        val workflowClass = group.loadClassFromMainBundles(className, Flow::class.java)
        val context = FrameworkUtil.getBundle(workflowClass).bundleContext
        val reference = context.getServiceReferences(Flow::class.java, "(component.name=$className)")
            .firstOrNull() ?: fail("No service found for $className.")
        return context.getService(reference)?.let { service ->
            try {
                @Suppress("unchecked_cast")
                service.call() as? T ?: fail("Workflow did not return the correct type.")
            } finally {
                context.ungetService(reference)
            }
        } ?: fail("$className service not available - OSGi error?")
    }
}
