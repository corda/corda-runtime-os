package net.corda.ledger.lib.impl.stub.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.common.json.serializers.standardTypesModule
import net.corda.internal.serialization.SerializedBytesImpl
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.serialization.SerializedBytes
import java.security.PublicKey

class StubSerializationService : SerializationServiceInternal {
    private val objectMapper = ObjectMapper().also {
        it.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        it.registerModule(JavaTimeModule())
        it.registerModule(KotlinModule.Builder().build())
        it.registerModule(standardTypesModule())
        val pkModule = SimpleModule()
        pkModule.addSerializer(PublicKey::class.java, PublicKeySerializer())
        pkModule.addDeserializer(PublicKey::class.java, PublicKeyDeserializer())
        pkModule.addSerializer(PrivacySalt::class.java, PrivacySaltSerializer())
        pkModule.addDeserializer(PrivacySalt::class.java, PrivacySaltDeserializer())
        pkModule.addSerializer(TransactionMetadata::class.java, TransactionMetadataSerializer())
        pkModule.addDeserializer(TransactionMetadata::class.java, TransactionMetadataDeserializer())
        it.registerModule(pkModule)
    }

    override fun <T : Any> serialize(obj: T, withCompression: Boolean): SerializedBytes<T> {
        return SerializedBytesImpl(objectMapper.writeValueAsBytes(obj))
    }

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return SerializedBytesImpl(objectMapper.writeValueAsBytes(obj))
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return objectMapper.readValue(serializedBytes.bytes, clazz)
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return objectMapper.readValue(bytes, clazz)
    }
}