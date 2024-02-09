package net.corda.flow.application.persistence.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.utilities.toByteArrays
import net.corda.utilities.toByteBuffers
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.persistence.query.OffsetResultSetExecutor
import net.corda.flow.state.FlowCheckpoint
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class NamedQueryExternalEventFactory : ExternalEventFactory<NamedQueryParameters, EntityResponse, OffsetResultSetExecutor.Results> {

    override val responseType = EntityResponse::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: NamedQueryParameters
    ): ExternalEventRecord {
        return ExternalEventRecord(
            payload = EntityRequest.newBuilder()
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setRequest(FindWithNamedQuery(parameters.queryName, parameters.parameters.toByteBuffers(), parameters.offset, parameters
                    .limit, null))
                .setFlowExternalEventContext(flowExternalEventContext)
                .build()
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EntityResponse): OffsetResultSetExecutor.Results {
        return OffsetResultSetExecutor.Results(
            serializedResults = response.results.toByteArrays(),
            numberOfRowsFromQuery = response.metadata.items.single { it.key == "numberOfRowsFromQuery" }.value.toInt()
        )
    }
}

@CordaSerializable
data class NamedQueryParameters(
    val queryName: String,
    val parameters: Map<String, ByteArray?>,
    val offset: Int,
    val limit: Int
)
