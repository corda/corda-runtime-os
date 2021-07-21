package net.corda.sandbox.cache

import net.corda.data.flow.FlowKey
import net.corda.data.identity.HoldingIdentity
import net.corda.install.InstallService
import net.corda.packaging.Cpb
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
) : SandboxCache {

    private val cache = ConcurrentHashMap<HoldingIdentity, SandboxGroup>()

    /**
     * Given a flow, where do I go for the cpb (and sandbox)
     */
    private val cpbForFlow = ConcurrentHashMap<FlowMetadata, Cpb.Identifier>()

    // This might need to go somewhere else...?
    override fun loadCpbs(
        CPBs: List<Path>,
    ) {
        for (cpbPath in CPBs) {
            // Can I assume this load has already happened?
            // Regardless I need the Cpb.Identifier
            val cpb = installService.loadCpb(Files.newInputStream(cpbPath))
            val cpbEx = installService.getCpb(cpb.identifier)
            cpbEx?.cpks?.forEach { cpk ->
                cpk.cordappManifest.flows.forEach { flow ->
                    // TODO: We don't know what to put for FlowKey here.  It might not belong in the key to the map.
                    cpbForFlow.computeIfAbsent(FlowMetadata(cpk.id, flow, FlowKey())) { cpb.identifier }
                }
            }
        }
    }

    override fun getSandboxGroupFor(identity: HoldingIdentity, flow: FlowMetadata): SandboxGroup {
        return cache.computeIfAbsent(identity) {
            val cpb = cpbForFlow[flow]
                ?: throw CordaRuntimeException("Flow not available in cordapp")
            sandboxService.createSandboxes(cpb)
        }
    }
}
