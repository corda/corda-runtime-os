package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.impl.DoubleSHA256DigestFactory
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.util.concurrent.ConcurrentHashMap

/**
 * A digest service singleton for everyone not inside a sandbox to share.
 */
@Component(service = [PlatformDigestService::class])
class PlatformDigestServiceImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata
) : PlatformDigestService {
    private val factories = ConcurrentHashMap<String, DigestAlgorithmFactory>().also {
        val factory = DoubleSHA256DigestFactory()
        it[factory.algorithm] = factory
    }
    private val lengths = ConcurrentHashMap<String, Int>()

    override fun hash(bytes: ByteArray, platformDigestName: DigestAlgorithmName): SecureHash {
        val hashBytes = digestFor(platformDigestName).digest(bytes)
        return SecureHash(platformDigestName.name, hashBytes)
    }

    override fun hash(inputStream: InputStream, platformDigestName: DigestAlgorithmName): SecureHash {
        val hashBytes = digestFor(platformDigestName).digest(inputStream)
        return SecureHash(platformDigestName.name, hashBytes)
    }

    override fun digestLength(platformDigestName: DigestAlgorithmName): Int =
        lengths.getOrPut(platformDigestName.name) {
            digestFor(platformDigestName).digestLength
        }

    private fun digestFor(digestAlgorithmName: DigestAlgorithmName): DigestAlgorithm =
        factories.getOrPut(digestAlgorithmName.name) {
            SpiDigestAlgorithmFactory(schemeMetadata, digestAlgorithmName.name)
        }.getInstance()

    private class SpiDigestAlgorithmFactory(
        schemeMetadata: _root_ide_package_.net.corda.crypto.cipher.suite.CipherSchemeMetadata,
        override val algorithm: String,
    ) : DigestAlgorithmFactory {
        companion object {
            const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE
        }

        private val provider: Provider = schemeMetadata.providers.getValue(
            schemeMetadata.digests.firstOrNull { it.algorithmName == algorithm }?.providerName
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
