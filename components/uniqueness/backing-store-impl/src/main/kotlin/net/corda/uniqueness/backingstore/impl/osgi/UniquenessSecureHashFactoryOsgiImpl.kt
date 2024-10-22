package net.corda.uniqueness.backingstore.impl.osgi

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Component

@Component(service = [ UniquenessSecureHashFactory::class ])
class UniquenessSecureHashFactoryOsgiImpl : UniquenessSecureHashFactory {
    override fun createSecureHash(algorithm: String, bytes: ByteArray): SecureHash {
        return SecureHashImpl(algorithm, bytes)
    }

    override fun getBytes(hash: SecureHash): ByteArray {
        return hash.bytes
    }

    override fun parseSecureHash(hashString: String): SecureHash {
        return net.corda.crypto.core.parseSecureHash(hashString)
    }
}
