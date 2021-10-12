package net.corda.virtual.node.cache

import net.corda.data.identity.HoldingIdentity
import net.corda.install.InstallService
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Component
class VirtualNodeCacheImpl @Activate constructor(
    @Reference(service = InstallService::class)
    private val installService: InstallService,
    @Reference(service = SandboxCreationService::class)
    private val sandboxCreationService: SandboxCreationService,
) : VirtualNodeCache {

    private val cache = ConcurrentHashMap<HoldingIdentity, SandboxGroup>()

    /**
     * Given a flow, where do I go for the cpb (and sandbox)
     */
    private val cpbForFlow = ConcurrentHashMap<String, CPI.Identifier>()

    // This might need to go somewhere else...?
    override fun loadCpbs(
        CPBs: List<Path>,
    ) {
        for (cpbPath in CPBs) {
            // Can I assume this load has already happened?
            // Regardless I need the Cpb.Identifier
            val cpb = installService.loadCpb(Files.newInputStream(cpbPath))
            val cpbEx = installService.getCpb(cpb.metadata.id)
            cpbEx?.cpks?.forEach { cpk ->
                cpk.metadata.cordappManifest.flows.forEach { flow ->
                    // HMM: We don't know what to put for FlowKey here.  It might not belong in the key to the map.
                    cpbForFlow.computeIfAbsent(flow) { cpb.metadata.id }
                }
            }
        }
    }

    override fun getSandboxGroupFor(identity: HoldingIdentity, flow: FlowMetadata): SandboxGroup {
        return cache.computeIfAbsent(identity) {
            val cpbIdentifier = cpbForFlow[flow.name]
                ?: throw CordaRuntimeException("Flow not available in cordapp")
            val cpb = installService.getCpb(cpbIdentifier)
                ?: throw CordaRuntimeException("Could not get cpb from its identifier $cpbIdentifier")
            sandboxCreationService.createSandboxGroup(cpb.cpks.map { it.metadata.hash })
        }
    }
}
