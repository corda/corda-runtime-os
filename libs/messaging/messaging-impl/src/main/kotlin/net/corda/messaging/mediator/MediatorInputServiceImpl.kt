package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.messaging.api.mediator.MediatorInputService
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Component(service = [MediatorInputService::class])
class MediatorInputServiceImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
): MediatorInputService {
    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> { }

    override fun <K : Any, E : Any> getHash(inputEvent: Record<K, E>): String {
        val recordValueBytes = serialize(inputEvent.value)
        check(recordValueBytes != null) {
            "Input record key and value bytes should not be null"
        }
        return UUID.nameUUIDFromBytes(recordValueBytes.sha256Bytes()).toString()
    }

    private fun serialize(value: Any?) = value?.let { serializer.serialize(it) }
}
