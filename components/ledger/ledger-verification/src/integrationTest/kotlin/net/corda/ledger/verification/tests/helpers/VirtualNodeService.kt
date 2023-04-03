package net.corda.ledger.verification.tests.helpers

import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Component(service = [ VirtualNodeService::class ])
class VirtualNodeService @Activate constructor(
    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,

    @Reference
    val verificationSandboxService: VerificationSandboxService,

    @Reference
    val sandboxGroupContextComponent: SandboxGroupContextComponent
) {
    private companion object {
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        fun generateHoldingIdentity() = createTestHoldingIdentity(X500_NAME, UUID.randomUUID().toString())
    }

    init {
        sandboxGroupContextComponent.initCaches(2)
    }

    fun load(resourceName: String): VirtualNodeInfo {
        return virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity())
    }
}
