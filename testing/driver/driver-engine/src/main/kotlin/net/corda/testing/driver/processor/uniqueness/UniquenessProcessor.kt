package net.corda.testing.driver.processor.uniqueness

import java.util.Collections.singletonList
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.records.Record
import net.corda.testing.driver.processor.ExternalProcessor
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.uniqueness.checker.impl.UniquenessCheckMessageProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ UniquenessProcessor::class ])
class UniquenessProcessor @Activate constructor(
    @Reference
    uniquenessChecker: UniquenessChecker,
    @Reference
    responseFactory: ExternalEventResponseFactory,
    @Reference
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : ExternalProcessor {
    private val stringDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, String::class.java)
    private val anyDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)
    private val processor = UniquenessCheckMessageProcessor(
        uniquenessChecker,
        responseFactory
    )

    override fun processEvent(record: Record<*, *>): List<Record<*, *>> {
        val flowId = requireNotNull((record.key as? ByteArray)?.let(stringDeserializer::deserialize)) {
            "Invalid or missing flow ID"
        }
        val request = requireNotNull((record.value as? ByteArray)?.let(anyDeserializer::deserialize) as? UniquenessCheckRequestAvro) {
            "Invalid or missing UniquenessCheckRequest"
        }

        return processor.onNext(singletonList(Record(record.topic, flowId, request)))
    }
}
