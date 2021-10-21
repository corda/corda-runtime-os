package net.corda.crypto.impl.serializer

import net.corda.v5.serialization.SerializationCustomSerializer
import org.osgi.service.component.annotations.Component
import java.security.PrivateKey

@Component(service = [SerializationCustomSerializer::class])
class PrivateKeySerializer : SerializationCustomSerializer<PrivateKey, String> {
    override fun toProxy(obj: PrivateKey): String = throw IllegalStateException("Attempt to serialise private key")
    override fun fromProxy(proxy: String): PrivateKey = throw IllegalStateException("Attempt to deserialise private key")
}