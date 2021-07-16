package net.corda.sandbox.cache

import net.corda.data.identity.HoldingIdentity
import net.corda.install.InstallService
import net.corda.packaging.Cpb
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.SandboxService
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Reference
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class SandboxCacheImpl @Activate constructor(
    @Reference
    private val installService: InstallService,
    @Reference
    private val sandboxService: SandboxService,
    CPBs: List<Path>,
) : SandboxCache {

    private val cache = ConcurrentHashMap<HoldingIdentity, SandboxGroup>()

    /**
     * Given a flow, where do I go for the cpb (and sandbox)
     */
    private val cpbForFlow = ConcurrentHashMap<FlowId, Cpb.Identifier>()

    // This might need to go somewhere else...?
    init {
        for (cpbPath in CPBs) {
            // Can I assume this load has already happened?
            // Regardless I need the Cpb.Identifier
            val cpb = installService.loadCpb(Files.newInputStream(cpbPath))
            val cpbEx = installService.getCpb(cpb.identifier)
            cpbEx?.cpks?.forEach { cpk ->
                cpk.cordappManifest.flows.forEach { flow ->
                    cpbForFlow.computeIfAbsent(FlowId(cpk.id, flow)) { cpb.identifier }
                }
            }
        }
    }

    override fun getSandboxFor(identity: HoldingIdentity, flow: FlowId): Sandbox {
        return getSandboxGroupFor(identity, flow).getSandbox(flow.cpkId)
    }

    private fun getSandboxGroupFor(identity: HoldingIdentity, flow: FlowId): SandboxGroup {
        return cache.computeIfAbsent(identity) {
            val cpb = cpbForFlow[flow]
                ?: throw CordaRuntimeException("Flow not available in cordapp")
            sandboxService.createSandboxes(cpb)
        }
    }
}
