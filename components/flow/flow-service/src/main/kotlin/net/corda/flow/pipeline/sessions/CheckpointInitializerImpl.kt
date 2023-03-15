package net.corda.flow.pipeline.sessions

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CheckpointInitializer::class])
class CheckpointInitializerImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService

) : CheckpointInitializer {
    override fun initialize(
        checkpoint: FlowCheckpoint,
        waitingFor: WaitingFor,
        holdingIdentity: HoldingIdentity,
        contextBuilder: (Set<SecureHash>) -> FlowStartContext
    ) {
        val vNodeInfo = virtualNodeInfoReadService.get(holdingIdentity)
            ?: throw FlowTransientException("Failed to find the virtual node info for holder '$holdingIdentity'")

        val cpiMetadata = cpiInfoReadService.get(vNodeInfo.cpiIdentifier)
            ?: throw FlowTransientException("Failed to find the cpiMetadata for identifier '${vNodeInfo.cpiIdentifier}'")

        val cpkFileHashes = cpiMetadata.cpksMetadata.mapTo(linkedSetOf(), CpkMetadata::fileChecksum)

        checkpoint.initFlowState(contextBuilder(cpkFileHashes), cpkFileHashes)
        checkpoint.waitingFor = waitingFor

    }
}