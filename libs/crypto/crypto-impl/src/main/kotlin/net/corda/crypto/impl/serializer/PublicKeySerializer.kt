package net.corda.crypto.impl.serializer

import net.corda.crypto.CryptoLibraryFactory
import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

/**
 * A serializer that writes out a public key in X.509 format.
 */
@Component(service = [InternalCustomSerializer::class])
class PublicKeySerializer @Activate constructor(
    @Reference(service = CryptoLibraryFactory::class)
    private val cryptoLibraryFactory: CryptoLibraryFactory
) : BaseDirectSerializer<PublicKey>() {
    override val type: Class<PublicKey> get() = PublicKey::class.java
    override val withInheritance: Boolean get() = true

    override fun writeObject(obj: PublicKey, writer: WriteObject)
        = writer.putAsBytes(cryptoLibraryFactory.getKeyEncodingService().encodeAsByteArray(obj))

    override fun readObject(reader: ReadObject): PublicKey
        = cryptoLibraryFactory.getKeyEncodingService().decodePublicKey(reader.getAsBytes())
}
