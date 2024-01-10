package net.corda.flow.application.persistence.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.persistence.query.OffsetResultSetExecutor
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer

@Component(service = [ExternalEventFactory::class])
class NamedQueryExternalEventFactory : ExternalEventFactory<NamedQueryParameters, EntityResponse, OffsetResultSetExecutor.Results> {

    override val responseType = EntityResponse::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: NamedQueryParameters
    ): ExternalEventRecord {
        return ExternalEventRecord(
            topic = Schemas.Persistence.PERSISTENCE_ENTITY_PROCESSOR_TOPIC,
            payload = EntityRequest.newBuilder()
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setRequest(FindWithNamedQuery(parameters.queryName, parameters.parameters, parameters.offset, parameters.limit, null))
                .setFlowExternalEventContext(flowExternalEventContext)
                .build()
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EntityResponse): OffsetResultSetExecutor.Results {
        return OffsetResultSetExecutor.Results(
            serializedResults = response.results,
            numberOfRowsFromQuery = response.metadata.items.single { it.key == "numberOfRowsFromQuery" }.value.toInt()
        )
    }
}

data class NamedQueryParameters(
    val queryName: String,
    val parameters: Map<String, ByteBuffer>,
    val offset: Int,
    val limit: Int
)
