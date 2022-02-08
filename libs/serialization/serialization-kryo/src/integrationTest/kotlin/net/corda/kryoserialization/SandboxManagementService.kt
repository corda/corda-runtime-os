package net.corda.kryoserialization

import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.testing.sandboxes.CpiLoaderService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

@Component(service = [ SandboxManagementService::class ])
class SandboxManagementService @Activate constructor(
    @Reference
    private val loader: CpiLoaderService,
    @Reference
    private val sandboxCreationService: SandboxCreationService
) {
    companion object {
        private const val CPI_ONE = "serializable-cpk-one-package.cpb"
        private const val CPI_TWO = "serializable-cpk-two-package.cpb"
    }

    val cpi1: CPI = loadCPI(resourceName = CPI_ONE)
    val cpi2: CPI = loadCPI(resourceName = CPI_TWO)
    val group1: SandboxGroup = createSandboxGroupFor(cpi1.cpks)
    val group2: SandboxGroup = createSandboxGroupFor(cpi2.cpks)

    @Suppress("unused")
    @Deactivate
    fun cleanup() {
        sandboxCreationService.unloadSandboxGroup(group1)
        sandboxCreationService.unloadSandboxGroup(group2)

        // Reset the CPI loader, ready for the next test.
        loader.stop()
    }

    private fun loadCPI(resourceName: String): CPI {
        return loader.loadCPI(resourceName)
    }

    private fun createSandboxGroupFor(cpks: Iterable<CPK>): SandboxGroup {
        return sandboxCreationService.createSandboxGroup(cpks)
    }
}
