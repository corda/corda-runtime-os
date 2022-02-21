package net.corda.sandboxtests

import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.testing.sandboxes.CpiLoader
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

@Component(service = [ SandboxFactory::class ])
class SandboxFactory @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,
    @Reference
    private val sandboxCreationService: SandboxCreationService,
    @Reference
    val sandboxContextService: SandboxContextService
) {
    val group1 = createSandboxGroupFor(CPI_ONE)
    val group2 = createSandboxGroupFor(CPI_THREE)

    fun createSandboxGroupFor(cpiResource: String): SandboxGroup {
        val cpi = cpiLoader.loadCPI(cpiResource)
        return sandboxCreationService.createSandboxGroup(cpi.cpks)
    }

    fun destroySandboxGroup(group: SandboxGroup) {
        sandboxCreationService.unloadSandboxGroup(group)
    }

    @Suppress("unused")
    @Deactivate
    fun done() {
        destroySandboxGroup(group1)
        destroySandboxGroup(group2)
        cpiLoader.stop()
    }
}
