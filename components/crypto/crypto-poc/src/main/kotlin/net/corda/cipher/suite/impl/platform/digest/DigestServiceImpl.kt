package net.corda.cipher.suite.impl.platform.digest

import net.corda.cipher.suite.impl.platform.handling.PlatformCipherSuiteMetadata
import net.corda.crypto.service.DigestAlgorithmFactoryProvider
import net.corda.crypto.service.DigestService
import net.corda.v5.cipher.suite.AbstractCipherSuite
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.util.concurrent.ConcurrentHashMap

@Component(service = [ DigestService::class ], scope = PROTOTYPE, property=["corda.system=true"])
class DigestServiceImpl @Activate constructor(
    @Reference(service = AbstractCipherSuite::class)
    private val suite: AbstractCipherSuite,
    @Reference(service = PlatformCipherSuiteMetadata::class)
    private val metadata: PlatformCipherSuiteMetadata,
    @Reference(
        service = DigestAlgorithmFactoryProvider::class,
        scope = PROTOTYPE_REQUIRED,
        cardinality = OPTIONAL
    )
    private val customFactoriesProvider: DigestAlgorithmFactoryProvider?
) : DigestService {
    private val factories = ConcurrentHashMap<String, DigestAlgorithmFactory>().also {
        val factory = DoubleSHA256DigestFactory()
        it[factory.algorithm] = factory
    }
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

    private fun digestFor(digestAlgorithmName: DigestAlgorithmName): DigestAlgorithm {
        return try {
            factories.getOrPut(digestAlgorithmName.name) {
                SpiDigestAlgorithmFactory(metadata, digestAlgorithmName.name)
            }.getInstance()
        } catch (e: IllegalArgumentException) {
            try {
                suite.findDigestAlgorithmFactory(digestAlgorithmName.name)
                    ?.getInstance()
                    ?: throw e
            } catch (e: IllegalArgumentException) {
                // Check any custom registered versions.
                customFactoriesProvider?.get(digestAlgorithmName.name)
                    ?.getInstance()
                    ?: throw e
            }
        }
    }

    private class SpiDigestAlgorithmFactory(
        metadata: PlatformCipherSuiteMetadata,
        override val algorithm: String,
    ) : DigestAlgorithmFactory {
        companion object {
            const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE
        }

        private val provider: Provider = metadata.providers.getValue(
            metadata.digests.firstOrNull { it.algorithmName == algorithm }?.providerName
                ?: throw IllegalArgumentException("Unknown hash algorithm $algorithm")
        )

        override fun getInstance(): DigestAlgorithm {
            try {
                val messageDigest = MessageDigest.getInstance(algorithm, provider)
                return MessageDigestWrapper(messageDigest, algorithm)
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalArgumentException("Unknown hash algorithm $algorithm for provider ${provider.name}")
            }
        }

        private class MessageDigestWrapper(
            val messageDigest: MessageDigest,
            override val algorithm: String
        ) : DigestAlgorithm {
            override val digestLength = messageDigest.digestLength
            override fun digest(bytes: ByteArray): ByteArray = messageDigest.digest(bytes)
            override fun digest(inputStream : InputStream): ByteArray {
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                while(true) {
                    val read = inputStream.read(buffer)
                    if(read <= 0) break
                    messageDigest.update(buffer, 0, read)
                }
                return messageDigest.digest()
            }
        }
    }
}
