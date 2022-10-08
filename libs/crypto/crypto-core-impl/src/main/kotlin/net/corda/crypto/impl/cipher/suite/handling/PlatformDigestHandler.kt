package net.corda.crypto.impl.cipher.suite.handling

import net.corda.crypto.impl.cipher.suite.PlatformCipherSuiteMetadata
import net.corda.v5.cipher.suite.handlers.digest.DigestHandler
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.util.concurrent.ConcurrentHashMap

class PlatformDigestHandler(
    private val suiteMetadata: PlatformCipherSuiteMetadata,
) : DigestHandler {
    private val factories = ConcurrentHashMap<String, DigestAlgorithmFactory>()
    private val lengths = ConcurrentHashMap<String, Int>()

    override val rank: Int = 0

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
            factories.getOrPut(digestAlgorithmName.name) {
                SpiDigestAlgorithmFactory(suiteMetadata, digestAlgorithmName.name)
            }.getInstance()

    private class SpiDigestAlgorithmFactory(
        suiteMetadata: PlatformCipherSuiteMetadata,
        override val algorithm: String,
    ) : DigestAlgorithmFactory {
        companion object {
            const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE
        }

        private val provider: Provider = suiteMetadata.providerForDigest(algorithm)

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
