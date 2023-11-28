package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.parseSecureHashAlgoName
import net.corda.crypto.core.parseSecureHashHexString
import net.corda.crypto.impl.DoubleSHA256DigestFactory
import net.corda.v5.base.util.ByteArrays
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
        return SecureHashImpl(platformDigestName.name, hashBytes)
    }

    override fun hash(inputStream: InputStream, platformDigestName: DigestAlgorithmName): SecureHash {
        val hashBytes = digestFor(platformDigestName).digest(inputStream)
        return SecureHashImpl(platformDigestName.name, hashBytes)
    }

    override fun parseSecureHash(algoNameAndHexString: String): SecureHash {
        val digestName = parseSecureHashAlgoName(algoNameAndHexString)
        // `digestLength` throws if algorithm not found/ not supported
        val digestHexStringLength = digestLength(DigestAlgorithmName(digestName)) * 2
        val hexString = parseSecureHashHexString(algoNameAndHexString)
        require(digestHexStringLength == hexString.length) {
            "Digest algorithm's: \"$digestName\" required hex string length: $digestHexStringLength " +
                "is not met by hex string: \"$hexString\""
        }
        return SecureHashImpl(digestName, ByteArrays.parseAsHex(hexString))
    }

    override fun digestLength(platformDigestName: DigestAlgorithmName): Int =
        lengths.getOrPut(platformDigestName.name) {
            digestFor(platformDigestName).digestLength
        }

    override fun defaultDigestAlgorithm(): DigestAlgorithmName =
        DigestAlgorithmName.SHA2_256

    companion object {
        val supportedDigestAlgorithms = linkedSetOf(
            DigestAlgorithmName.SHA2_256,
            DigestAlgorithmName.SHA2_256D,
            DigestAlgorithmName.SHA2_384,
            DigestAlgorithmName.SHA2_512
        )
    }

    override fun supportedDigestAlgorithms(): Set<DigestAlgorithmName> =
        supportedDigestAlgorithms

    private fun digestFor(digestAlgorithmName: DigestAlgorithmName): DigestAlgorithm =
        factories.getOrPut(digestAlgorithmName.name) {
            SpiDigestAlgorithmFactory(schemeMetadata, digestAlgorithmName.name)
        }.instance

    private class SpiDigestAlgorithmFactory(
        schemeMetadata: CipherSchemeMetadata,
        private val algorithm: String
    ) : DigestAlgorithmFactory {
        companion object {
            const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE
        }

        private val provider: Provider = schemeMetadata.providers.getValue(
            schemeMetadata.digests.firstOrNull { it.algorithmName == algorithm }?.providerName
                ?: throw IllegalArgumentException("Unknown hash algorithm $algorithm")
        )

        override fun getAlgorithm() = algorithm

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
            private val algorithm: String
        ) : DigestAlgorithm {
            override fun getAlgorithm() = algorithm

            override fun getDigestLength() = messageDigest.digestLength

            override fun digest(bytes: ByteArray): ByteArray = messageDigest.digest(bytes)
            override fun digest(inputStream: InputStream): ByteArray {
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) break
                    messageDigest.update(buffer, 0, read)
                }
                return messageDigest.digest()
            }
        }
    }
}
