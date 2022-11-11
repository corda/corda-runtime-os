package net.corda.crypto.impl.serialization

import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import net.corda.v5.cipher.suite.KeyEncodingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

/**
 * A serializer that writes out a public key in X.509 format.
 */
@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    scope = ServiceScope.PROTOTYPE
)
class PublicKeySerializer @Activate constructor(
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : BaseDirectSerializer<PublicKey>(), UsedByFlow, UsedByPersistence, UsedByVerification {
    override val type: Class<PublicKey> get() = PublicKey::class.java
    override val withInheritance: Boolean get() = true

    override fun writeObject(obj: PublicKey, writer: WriteObject)
        = writer.putAsBytes(keyEncodingService.encodeAsByteArray(obj))

    override fun readObject(reader: ReadObject): PublicKey
        = keyEncodingService.decodePublicKey(reader.getAsBytes())
}
