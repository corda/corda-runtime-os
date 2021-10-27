package net.corda.crypto.impl.serializer

import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer
import org.osgi.service.component.annotations.Component
import java.security.PrivateKey

@Component(service = [SerializationCustomSerializer::class])
class PrivateKeySerializer : SerializationCustomSerializer<PrivateKey, String> {
    override fun toProxy(obj: PrivateKey, context: SerializationContext): String = throw IllegalStateException("Attempt to serialise private key")
    override fun fromProxy(proxy: String, context: SerializationContext): PrivateKey = throw IllegalStateException("Attempt to deserialise private key")
}