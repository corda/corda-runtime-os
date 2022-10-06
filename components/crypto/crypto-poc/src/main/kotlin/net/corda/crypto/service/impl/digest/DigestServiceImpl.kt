package net.corda.crypto.service.impl.digest

import net.corda.crypto.service.DigestAlgorithmFactoryProvider
import net.corda.crypto.service.DigestService
import net.corda.v5.cipher.suite.AbstractCipherSuite
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.DigestAlgorithm
import org.bouncycastle.crypto.CryptoException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

@Component(service = [ DigestService::class ], scope = PROTOTYPE, property=["corda.system=true"])
class DigestServiceImpl @Activate constructor(
    @Reference(service = AbstractCipherSuite::class)
    private val suite: AbstractCipherSuite,
    @Reference(
        service = DigestAlgorithmFactoryProvider::class,
        scope = PROTOTYPE_REQUIRED,
        cardinality = OPTIONAL
    )
    private val customFactoriesProvider: DigestAlgorithmFactoryProvider?
) : DigestService {
    private val lengths = ConcurrentHashMap<String, Int>()

    override fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash {
        val hashBytes = digestFor(digestAlgorithmName).digest(bytes)
        return SecureHash(digestAlgorithmName.name, hashBytes)
    }

    override fun hash(inputStream: InputStream, digestAlgorithmName: DigestAlgorithmName): SecureHash {
        val hashBytes = digestFor(digestAlgorithmName).digest(inputStream)
        return SecureHash(digestAlgorithmName.name, hashBytes)
    }

    override fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int =
        lengths.getOrPut(digestAlgorithmName.name) {
            return digestFor(digestAlgorithmName).digestLength
        }

    private fun digestFor(digestAlgorithmName: DigestAlgorithmName): DigestAlgorithm =
        try {
            suite.findDigestAlgorithmFactory(digestAlgorithmName.name)
                ?.getInstance() ?: throw CryptoException("$digestAlgorithmName not found")
        } catch (e: IllegalArgumentException) {
            // Check any custom registered versions.
            customFactoriesProvider?.get(digestAlgorithmName.name)
                ?.getInstance()
                ?: throw e
        }
}
