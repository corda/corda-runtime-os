package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.data.services.TokenClaimRelease
import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenSetKey
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class ClaimedTokenReleaseRequestHandler @Activate constructor(
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.ClaimedTokenRelease> {

    override val type = FlowIORequest.ClaimedTokenRelease::class.java

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.ClaimedTokenRelease
    ): WaitingFor {
        return WaitingFor(Wakeup())
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.ClaimedTokenRelease
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint
        val releaseRequest = request.claimedTokensRelease
        val key = TokenSetKey().apply {
            shortHolderId = checkpoint.holdingIdentity.shortHash
            tokenType = releaseRequest.tokenType
            issuerHash = releaseRequest.issuerHash
            notaryHash = releaseRequest.notaryHash
            symbol = releaseRequest.symbol
        }

        val claimReleaseMessage = TokenClaimRelease().apply {
            requestContext = ExternalEventContext().apply {
                flowId = checkpoint.flowId
                requestId = releaseRequest.claimId
            }
            tokenSetKey = key
            usedTokenRefs = releaseRequest.usedTokenRefs
            releasedTokenRefs = releaseRequest.releasedTokenRefs
        }

        val eventMessage = TokenEvent().apply {
            tokenSetKey = key
            payload = claimReleaseMessage
        }

        val records = listOf(
            Record(Schemas.Services.TOKEN_CACHE_EVENT, key, eventMessage),
            flowRecordFactory.createFlowEventRecord(checkpoint.flowId, net.corda.data.flow.event.Wakeup())
        )

        return context.copy(outputRecords = context.outputRecords + records)
    }
}