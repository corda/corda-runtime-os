package net.corda.testing.driver.ledger.impl

import java.util.Collections.singletonList
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.processor.DelegatedRequestHandlerSelector
import net.corda.ledger.persistence.processor.PersistenceRequestProcessor
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ LedgerProcessor::class ])
class LedgerProcessor @Activate constructor(
    @Reference
    entitySandboxService: EntitySandboxService,
    @Reference
    delegatedRequestHandlerSelector: DelegatedRequestHandlerSelector,
    @Reference
    responseFactory: ResponseFactory,
    @Reference
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) {
    private val stringDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, String::class.java)
    private val anyDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)
    private val processor = PersistenceRequestProcessor(
        entitySandboxService,
        delegatedRequestHandlerSelector,
        responseFactory
    )

    fun processEvent(record: Record<*, *>): Record<String, FlowEvent> {
        val flowId = requireNotNull((record.key as? ByteArray)?.let(stringDeserializer::deserialize)) {
            "Invalid or missing flow ID"
        }
        val request = requireNotNull((record.value as? ByteArray)?.let(anyDeserializer::deserialize) as? LedgerPersistenceRequest) {
            "Invalid or missing LedgerPersistenceRequest"
        }

        val responses = processor.onNext(singletonList(Record(record.topic, flowId, request)))
        @Suppress("unchecked_cast")
        return responses.single() as Record<String, FlowEvent>
    }
}
