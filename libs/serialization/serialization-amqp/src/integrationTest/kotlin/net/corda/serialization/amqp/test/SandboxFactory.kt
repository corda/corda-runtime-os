package net.corda.serialization.amqp.test

import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.testing.sandboxes.CpiLoader
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ SandboxFactory::class ])
class SandboxFactory @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,
    @Reference
    private val sandboxCreationService: SandboxCreationService
) {
    fun loadSandboxGroup(resourceName: String): SandboxGroup {
        return cpiLoader.loadCPI(resourceName).let { cpi ->
            sandboxCreationService.createSandboxGroup(cpi.cpks, "FLOW")
        }
    }

    fun unloadSandboxGroup(sandboxGroup: SandboxGroup) {
        sandboxCreationService.unloadSandboxGroup(sandboxGroup)
    }
}
