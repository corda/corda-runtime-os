package net.corda.crypto.impl.serializer

import net.corda.crypto.CryptoLibraryFactory
import net.corda.v5.serialization.SerializationCustomSerializer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

/**
 * A serializer that writes out a public key in X.509 format.
 */
@Component(service = [SerializationCustomSerializer::class])
class PublicKeySerializer @Activate constructor(
    @Reference(service = CryptoLibraryFactory::class)
    private val cryptoLibraryFactory: CryptoLibraryFactory
) : SerializationCustomSerializer<PublicKey, ByteArray> {

    override fun toProxy(obj: PublicKey): ByteArray =
        cryptoLibraryFactory.getKeyEncodingService().encodeAsByteArray(obj)

    override fun fromProxy(proxy: ByteArray): PublicKey =
        cryptoLibraryFactory.getKeyEncodingService().decodePublicKey(proxy)
}