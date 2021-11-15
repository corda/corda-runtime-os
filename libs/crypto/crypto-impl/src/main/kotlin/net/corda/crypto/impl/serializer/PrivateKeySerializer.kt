package net.corda.crypto.impl.serializer

import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.InternalDirectSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import net.corda.serialization.SerializationContext
import org.osgi.service.component.annotations.Component
import java.io.NotSerializableException
import java.security.PrivateKey

@Component(service = [InternalCustomSerializer::class])
class PrivateKeySerializer : InternalDirectSerializer<PrivateKey> {
    override val type: Class<PrivateKey> get() = PrivateKey::class.java
    override val withInheritance: Boolean get() = true

    override fun readObject(reader: ReadObject, context: SerializationContext): PrivateKey
        = throw NotSerializableException("Attempt to serialise private key")

    override fun writeObject(obj: PrivateKey, writer: WriteObject, context: SerializationContext)
        = throw NotSerializableException("Attempt to deserialise private key")
}
