package net.corda.testing.sandboxes

import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

@Component(service = [ SandboxManagementService::class ])
class SandboxManagementService @Activate constructor(
    @Reference
    private val sandboxCreationService: SandboxCreationService
) {
    val group1: SandboxGroup = sandboxCreationService.createSandboxGroup(emptyList())

    @Suppress("unused")
    @Deactivate
    fun cleanup() {
        sandboxCreationService.unloadSandboxGroup(group1)
    }
}