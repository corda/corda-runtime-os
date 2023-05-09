package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.persistence.query.ResultSetExecutor
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class VaultNamedQueryExternalEventFactory(
    private val clock: Clock = Clock.systemUTC()
) : ExternalEventFactory<VaultNamedQueryEventParams, EntityResponse, ResultSetExecutor.Results> {

    override val responseType = EntityResponse::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: VaultNamedQueryEventParams
    ): ExternalEventRecord {
        return ExternalEventRecord(
            topic = Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC,
            payload = LedgerPersistenceRequest.newBuilder()
                .setTimestamp(clock.instant())
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setRequest(
                    FindWithNamedQuery(
                        parameters.queryName,
                        parameters.queryParameters,
                        parameters.offset,
                        parameters.limit
                    )
                )
                .setFlowExternalEventContext(flowExternalEventContext)
                .setLedgerType(LedgerTypes.UTXO)
                .build()
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EntityResponse): ResultSetExecutor.Results {
        return ResultSetExecutor.Results(
            serializedResults = response.results,
            numberOfRowsFromQuery = response.metadata.items.single { it.key == "numberOfRowsFromQuery" }.value.toInt()
        )
    }
}

data class VaultNamedQueryEventParams(
    val queryName: String,
    val queryParameters: Map<String, ByteBuffer>,
    val offset: Int,
    val limit: Int
)
