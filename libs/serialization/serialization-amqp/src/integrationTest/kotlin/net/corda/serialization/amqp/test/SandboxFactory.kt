package net.corda.serialization.amqp.test

import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.testing.sandboxes.CpiLoaderService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

@Component(service = [ SandboxFactory::class ])
class SandboxFactory @Activate constructor(
    @Reference
    private val loader: CpiLoaderService,
    @Reference
    private val sandboxCreationService: SandboxCreationService
) {
    @Suppress("unused")
    @Deactivate
    fun done() {
        loader.stop()
    }

    fun loadSandboxGroup(resourceName: String): SandboxGroup {
        return loader.loadCPI(resourceName).let { cpi ->
            sandboxCreationService.createSandboxGroup(cpi.cpks)
        }
    }

    fun unloadSandboxGroup(sandboxGroup: SandboxGroup) {
        sandboxCreationService.unloadSandboxGroup(sandboxGroup)
    }
}
