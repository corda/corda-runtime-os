package net.corda.ledger.persistence.query.execution.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryRequest
import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryResponse
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer

@Component(service = [ExternalEventFactory::class])
class VaultNamedQueryExternalEventFactory @Activate constructor():
    ExternalEventFactory<VaultNamedQueryEventParams, ExecuteVaultNamedQueryResponse, List<ByteBuffer>> {

    override val responseType = ExecuteVaultNamedQueryResponse::class.java

    override fun resumeWith(
        checkpoint: FlowCheckpoint,
        response: ExecuteVaultNamedQueryResponse
    ): List<ByteBuffer> {
        return response.results
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: VaultNamedQueryEventParams
    ): ExternalEventRecord {
        return ExternalEventRecord(
            topic = Schemas.Persistence.PERSISTENCE_LEDGER_NAMED_QUERY_TOPIC,
            payload = createRequest(parameters, flowExternalEventContext, checkpoint)
        )
    }

    private fun createRequest(params: VaultNamedQueryEventParams,
                              context: ExternalEventContext,
                              checkpoint: FlowCheckpoint) = ExecuteVaultNamedQueryRequest(
        checkpoint.holdingIdentity.toAvro(),
        context,
        params.queryName,
        params.queryParameters,
        params.offset,
        params.limit
    )
}

data class VaultNamedQueryEventParams(
    val queryName: String,
    val queryParameters: Map<String, ByteBuffer>,
    val offset: Int,
    val limit: Int
)
