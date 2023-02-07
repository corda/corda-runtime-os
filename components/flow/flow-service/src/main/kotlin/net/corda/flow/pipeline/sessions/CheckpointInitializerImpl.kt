package net.corda.flow.pipeline.sessions

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
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
    override fun initialize(checkpoint: FlowCheckpoint, startContext: FlowStartContext, waitingFor: WaitingFor) {
        checkpoint.waitingFor = waitingFor
        val vNodeInfo = virtualNodeInfoReadService.get(startContext.identity.toCorda())
        checkNotNull(vNodeInfo) {
            "Failed to find the virtual node info for holder '${startContext.identity.toCorda()}'"
        }
        val cpiMetadata = cpiInfoReadService.get(vNodeInfo.cpiIdentifier)
        checkNotNull(cpiMetadata) {
            "Failed to find the cpiMetadata for identifier '${vNodeInfo.cpiIdentifier}'"
        }
        val cpks = cpiMetadata.cpksMetadata.mapTo(linkedSetOf(), CpkMetadata::fileChecksum)
        checkpoint.initFlowState(startContext, cpks)

    }
}