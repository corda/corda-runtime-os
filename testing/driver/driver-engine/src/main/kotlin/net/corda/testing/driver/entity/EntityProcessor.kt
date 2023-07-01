package net.corda.testing.driver.entity

import java.util.Collections.singletonList
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.EntityRequest
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.PayloadChecker
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.testing.driver.config.SmartConfigProvider
import net.corda.testing.driver.flow.ExternalProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ EntityProcessor::class, ExternalProcessor::class ])
class EntityProcessor @Activate constructor(
    @Reference
    currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference
    entitySandboxService: EntitySandboxService,
    @Reference
    responseFactory: ResponseFactory,
    @Reference
    smartConfigProvider: SmartConfigProvider,
    @Reference
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : ExternalProcessor {
    private val stringDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, String::class.java)
    private val anyDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)
    private val processor = EntityMessageProcessor(
        currentSandboxGroupContext,
        entitySandboxService,
        responseFactory,
        PayloadChecker(smartConfigProvider.smartConfig)::checkSize
    )

    override fun processEvent(record: Record<*, *>): Record<String, FlowEvent> {
        val flowId = requireNotNull((record.key as? ByteArray)?.let(stringDeserializer::deserialize)) {
            "Invalid or missing flow ID"
        }
        val request = requireNotNull((record.value as? ByteArray)?.let(anyDeserializer::deserialize) as? EntityRequest) {
            "Invalid or missing EntityRequest"
        }

        val responses = processor.onNext(singletonList(Record(record.topic, flowId, request)))
        @Suppress("unchecked_cast")
        return responses.single() as Record<String, FlowEvent>
    }
}
