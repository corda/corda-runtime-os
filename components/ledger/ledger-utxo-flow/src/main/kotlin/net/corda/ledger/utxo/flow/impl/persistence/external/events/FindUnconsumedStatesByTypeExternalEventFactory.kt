package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindUnconsumedStatesByType
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.TransactionOutputs
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.getEncumbranceGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.schema.Schemas
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindUnconsumedStatesByTypeExternalEventFactory @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    private val clock: Clock = Clock.systemUTC()
) : ExternalEventFactory<FindUnconsumedStatesByTypeParameters, TransactionOutputs, List<StateAndRef<ContractState>>>
{
    override val responseType = TransactionOutputs::class.java

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

    override fun resumeWith(checkpoint: FlowCheckpoint, response: TransactionOutputs): List<StateAndRef<ContractState>> {
        return response.transactionOutputs.map {
            val info = serializationService.deserialize<UtxoOutputInfoComponent>(it.info.array())
            val contractState = serializationService.deserialize<ContractState>(it.data.array())
            StateAndRefImpl(
                state = TransactionStateImpl(contractState, info.notary, info.getEncumbranceGroup()),
                ref = StateRef(SecureHash.parse(it.id), it.index)
            )
        }
    }
}

data class FindUnconsumedStatesByTypeParameters(
    val stateClass: Class<*>
)