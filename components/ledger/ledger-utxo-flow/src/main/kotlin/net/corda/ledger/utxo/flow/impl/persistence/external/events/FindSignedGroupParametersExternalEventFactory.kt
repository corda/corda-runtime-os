package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindSignedGroupParameters
import net.corda.data.ledger.persistence.FindSignedGroupParametersResponse
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindSignedGroupParametersExternalEventFactory(
    private val groupParametersFactory: GroupParametersFactory,
    private val clock: Clock = Clock.systemUTC()
) : ExternalEventFactory<FindSignedGroupParametersParameters, FindSignedGroupParametersResponse, List<SignedGroupParameters>> {

    @Activate constructor(
        @Reference(service = GroupParametersFactory::class)
        groupParametersFactory: GroupParametersFactory,
    ) : this(groupParametersFactory, Clock.systemUTC())

    override val responseType = FindSignedGroupParametersResponse::class.java

    fun createRequest(parameters: FindSignedGroupParametersParameters): Any {
        return FindSignedGroupParameters(parameters.hash)
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: FindSignedGroupParametersParameters
    ): ExternalEventRecord {
        return ExternalEventRecord(
            payload = LedgerPersistenceRequest.newBuilder()
                .setTimestamp(clock.instant())
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setRequest(createRequest(parameters))
                .setFlowExternalEventContext(flowExternalEventContext)
                .setLedgerType(LedgerTypes.UTXO)
                .build()
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: FindSignedGroupParametersResponse): List<SignedGroupParameters> {
        return response.results.map {
            requireNotNull(it.mgmSignature) {
                "Received GroupParameters needs to be signed."
            }
            requireNotNull(it.mgmSignatureSpec) {
                "Received GroupParameters needs a signature specification."
            }
            groupParametersFactory.create(it) as SignedGroupParameters
        }
    }
}

data class FindSignedGroupParametersParameters(val hash: String)