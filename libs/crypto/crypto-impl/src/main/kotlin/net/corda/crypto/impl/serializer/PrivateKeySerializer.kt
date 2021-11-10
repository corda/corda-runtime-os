package net.corda.crypto.impl.serializer

import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.SerializationContext
import org.osgi.service.component.annotations.Component
import java.io.NotSerializableException
import java.security.PrivateKey

@Component(service = [InternalCustomSerializer::class])
class PrivateKeySerializer : InternalCustomSerializer<PrivateKey, String> {
    override val type: Class<PrivateKey> get() = PrivateKey::class.java
    override val proxyType: Class<String> get() = String::class.java
    override val withInheritance: Boolean get() = true

    override fun toProxy(obj: PrivateKey, context: SerializationContext): String
        = throw NotSerializableException("Attempt to serialise private key")
    override fun fromProxy(proxy: String, context: SerializationContext): PrivateKey
        = throw NotSerializableException("Attempt to deserialise private key")
}
