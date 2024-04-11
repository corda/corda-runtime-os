package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorInputService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.Base64

@Component(service = [MediatorInputService::class])
class MediatorInputServiceImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
): MediatorInputService {
    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> { }

    override fun <K : Any, E : Any> getHash(inputEvent: CordaConsumerRecord<K, E>): String {
        val recordValueBytes = serialize(inputEvent.value)
        check(recordValueBytes != null) {
            "Input record key and value bytes should not be null"
        }
        return hash(recordValueBytes).toBase64()
    }

    private fun serialize(value: Any?) = value?.let { serializer.serialize(it) }

    private fun hash(bytes: ByteArray) = bytes.sha256Bytes()

    private fun ByteArray.toBase64(): String =
        String(Base64.getEncoder().encode(this))
}
