package net.corda.crypto.impl.serializer

import net.corda.crypto.CryptoLibraryFactory
import net.corda.serialization.InternalCustomSerializer
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
) : InternalCustomSerializer<PublicKey, ByteArray> {
    override val type: Class<PublicKey> get() = PublicKey::class.java
    override val proxyType: Class<ByteArray> get() = ByteArray::class.java
    override val withInheritance: Boolean get() = true

    override fun toProxy(obj: PublicKey): ByteArray =
        cryptoLibraryFactory.getKeyEncodingService().encodeAsByteArray(obj)

    override fun fromProxy(proxy: ByteArray): PublicKey =
        cryptoLibraryFactory.getKeyEncodingService().decodePublicKey(proxy)
}
