package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.handlers.waiting.WaitingForStartFlow
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventHandler::class])
class StartFlowEventHandler @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : FlowEventHandler<StartFlow> {

    override val type = StartFlow::class.java

    override fun preProcess(context: FlowEventContext<StartFlow>): FlowEventContext<StartFlow> {
        println("********StartFlowEventHandler.PreProcess flowStartArgs ***********")

        context.checkpoint.initFlowState(context.inputEventPayload.startContext)
        context.checkpoint.waitingFor =  WaitingFor(WaitingForStartFlow)

        val holdingIdentity = context.inputEventPayload.startContext.identity.toCorda()

//        val virtualNodeInfo = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentity.shortHash)
        val virtualNodeInfo = virtualNodeInfoReadService.get(holdingIdentity)
        println("virtualNodeInfo.flowStartOperationalStatus: ${virtualNodeInfo?.flowStartOperationalStatus}")

        if (virtualNodeInfo?.flowStartOperationalStatus == OperationalStatus.INACTIVE) {
            throw FlowMarkedForKillException(
                "flowStartOperationalStatus is INACTIVE, new flows cannot be started for ${holdingIdentity.x500Name}"
            )
        }

        return context
    }
}