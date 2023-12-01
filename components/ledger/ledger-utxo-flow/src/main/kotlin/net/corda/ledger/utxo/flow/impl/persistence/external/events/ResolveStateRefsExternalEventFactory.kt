package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.crypto.core.bytes
import net.corda.data.crypto.SecureHash
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.ResolveStateRefs
import net.corda.data.ledger.persistence.UtxoTransactionOutputs
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class ResolveStateRefsExternalEventFactory(
    private val clock: Clock = Clock.systemUTC()
) : ExternalEventFactory<ResolveStateRefsParameters, UtxoTransactionOutputs, List<UtxoVisibleTransactionOutputDto>>
{
    @Activate
    constructor() : this(Clock.systemUTC())

    override val responseType = UtxoTransactionOutputs::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: ResolveStateRefsParameters
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

    private fun createRequest(parameters: ResolveStateRefsParameters): Any {
        return ResolveStateRefs(parameters.stateRefs.map {
            net.corda.data.ledger.utxo.StateRef(
                SecureHash(
                    it.transactionId.algorithm,
                    ByteBuffer.wrap(it.transactionId.bytes)
                ), it.index
            )
        })
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: UtxoTransactionOutputs): List<UtxoVisibleTransactionOutputDto> {
        return response.transactionOutputs.map {
            UtxoVisibleTransactionOutputDto(it.transactionId, it.index, it.info.array(), it.data.array())
        }
    }
}

data class ResolveStateRefsParameters(
    val stateRefs: Iterable<StateRef>
)