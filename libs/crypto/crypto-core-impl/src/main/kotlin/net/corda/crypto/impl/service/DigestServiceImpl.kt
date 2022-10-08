package net.corda.crypto.impl.service

import net.corda.crypto.core.service.DigestAlgorithmFactoryProvider
import net.corda.crypto.core.service.DigestService
import net.corda.v5.cipher.suite.CipherSuiteBase
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

@Component(service = [ DigestService::class ])
class DigestServiceImpl @Activate constructor(
    @Reference(service = CipherSuiteBase::class)
    private val suite: CipherSuiteBase,
    @Reference(
        service = DigestAlgorithmFactoryProvider::class,
        scope = PROTOTYPE_REQUIRED,
        cardinality = OPTIONAL
    )
    private val customFactoriesProvider: DigestAlgorithmFactoryProvider?
) : DigestService {
    private val lengths = ConcurrentHashMap<String, Int>()

    override fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash {
        val hash = suite.findDigestHandler(digestAlgorithmName.name)?.hash(bytes, digestAlgorithmName)
        if(hash != null) {
            return hash
        }
        val hashBytes = customFactoriesProvider?.get(digestAlgorithmName.name)?.getInstance()?.digest(bytes)
        if(hashBytes != null) {
            return SecureHash(digestAlgorithmName.name, hashBytes)
        }
        throw IllegalArgumentException("Cannot compute $digestAlgorithmName")
    }

    override fun hash(inputStream: InputStream, digestAlgorithmName: DigestAlgorithmName): SecureHash {
        val hash = suite.findDigestHandler(digestAlgorithmName.name)?.hash(inputStream, digestAlgorithmName)
        if(hash != null) {
            return hash
        }
        val hashBytes = customFactoriesProvider?.get(digestAlgorithmName.name)?.getInstance()?.digest(inputStream)
        if(hashBytes != null) {
            return SecureHash(digestAlgorithmName.name, hashBytes)
        }
        throw IllegalArgumentException("Cannot compute $digestAlgorithmName")
    }

    override fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int =
        lengths.getOrPut(digestAlgorithmName.name) {
            var digestLength = suite.findDigestHandler(digestAlgorithmName.name)?.digestLength(digestAlgorithmName)
            if(digestLength != null) {
                return@getOrPut digestLength
            }
            digestLength = customFactoriesProvider?.get(digestAlgorithmName.name)?.getInstance()?.digestLength
            if(digestLength != null) {
                return@getOrPut digestLength
            }
            return@getOrPut -1
        }.also { if (it <=0 ) throw IllegalArgumentException("Cannot get digest length for $digestAlgorithmName") }
}
