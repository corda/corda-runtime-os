package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindUnconsumedStatesByType
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.UtxoTransactionOutputs
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.flow.impl.persistence.UtxoTransactionOutputDto
import net.corda.schema.Schemas
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindUnconsumedStatesByTypeExternalEventFactory(
    private val clock: Clock
) : ExternalEventFactory<FindUnconsumedStatesByTypeParameters, UtxoTransactionOutputs, List<UtxoTransactionOutputDto>>
{
    @Activate
    constructor() : this(Clock.systemUTC())

    override val responseType = UtxoTransactionOutputs::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: FindUnconsumedStatesByTypeParameters
    ): ExternalEventRecord {
        return ExternalEventRecord(
            topic = Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC,
            payload = LedgerPersistenceRequest.newBuilder()
                .setTimestamp(clock.instant())
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setRequest(createRequest(parameters))
                .setFlowExternalEventContext(flowExternalEventContext)
                .setLedgerType(LedgerTypes.UTXO)
                .build()
        )
    }

    private fun createRequest(parameters: FindUnconsumedStatesByTypeParameters): Any {
        return FindUnconsumedStatesByType(parameters.stateClass.canonicalName)
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: UtxoTransactionOutputs): List<UtxoTransactionOutputDto> {
        return response.transactionOutputs.map {
            UtxoTransactionOutputDto(it.transactionId, it.index, it.info.array(), it.data.array())
        }
    }
}

data class FindUnconsumedStatesByTypeParameters(
    val stateClass: Class<*>
)