package net.corda.testing.driver.processor.verify

import java.util.Collections.singletonList
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.verification.processor.impl.VerificationRequestHandlerImpl
import net.corda.ledger.verification.processor.impl.VerificationRequestProcessor
import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.testing.driver.processor.ExternalProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ VerifyProcessor::class ])
class VerifyProcessor @Activate constructor(
    @Reference
    currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference
    verificationSandboxService: VerificationSandboxService,
    @Reference
    responseFactory: ExternalEventResponseFactory,
    @Reference
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
): ExternalProcessor {
    private val stringDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, String::class.java)
    private val anyDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)
    private val processor = VerificationRequestProcessor(
        currentSandboxGroupContext,
        verificationSandboxService,
        VerificationRequestHandlerImpl(responseFactory),
        responseFactory
    )

    override fun processEvent(record: Record<*, *>): List<Record<*, *>> {
        val flowId = requireNotNull((record.key as? ByteArray)?.let(stringDeserializer::deserialize)) {
            "Invalid or missing flow ID"
        }
        val request = requireNotNull((record.value as? ByteArray)?.let(anyDeserializer::deserialize) as? TransactionVerificationRequest) {
            "Invalid or missing TransactionVerificationRequest"
        }

        return processor.onNext(singletonList(Record(record.topic, flowId, request)))
    }
}
