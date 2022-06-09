package net.corda.kryoserialization

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.testing.sandboxes.CpiLoader
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

@Component(service = [ SandboxManagementService::class ])
class SandboxManagementService @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,
    @Reference
    private val sandboxCreationService: SandboxCreationService
) {
    companion object {
        private const val CPI_ONE = "serializable-cpk-one-package.cpb"
        private const val CPI_TWO = "serializable-cpk-two-package.cpb"
    }

    val cpi1: Cpi = loadCPI(resourceName = CPI_ONE)
    val cpi2: Cpi = loadCPI(resourceName = CPI_TWO)
    val group1: SandboxGroup = createSandboxGroupFor(cpi1.cpks)
    val group2: SandboxGroup = createSandboxGroupFor(cpi2.cpks)

    @Suppress("unused")
    @Deactivate
    fun cleanup() {
        sandboxCreationService.unloadSandboxGroup(group1)
        sandboxCreationService.unloadSandboxGroup(group2)
    }

    private fun loadCPI(resourceName: String): Cpi {
        return cpiLoader.loadCPI(resourceName)
    }

    private fun createSandboxGroupFor(cpks: Iterable<Cpk>): SandboxGroup {
        return sandboxCreationService.createSandboxGroup(cpks)
    }
}
