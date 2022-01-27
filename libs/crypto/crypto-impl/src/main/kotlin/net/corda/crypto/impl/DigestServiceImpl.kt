package net.corda.crypto.impl

import net.corda.crypto.DigestAlgorithmFactoryProvider
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.util.concurrent.ConcurrentHashMap

class DigestServiceImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val customDigestAlgorithmFactories: List<DigestAlgorithmFactory>,
    private val customFactoriesProvider: DigestAlgorithmFactoryProvider?
) : DigestService {
    private val factories = ConcurrentHashMap<String, DigestAlgorithmFactory>().also { factories ->
        customDigestAlgorithmFactories.forEach { factory ->
            factories[factory.algorithm] = factory
        }
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
        try {
            return factories.getOrPut(digestAlgorithmName.name) {
                SpiDigestAlgorithmFactory(schemeMetadata, digestAlgorithmName.name)
            }.getInstance()
        } catch (e: IllegalArgumentException) {
            // Check any custom registered versions.
            val digestAlgorithmFactory = customFactoriesProvider?.get(digestAlgorithmName.name)
            if (digestAlgorithmFactory != null) {
                return digestAlgorithmFactory.getInstance()
            }

            throw e
        }
    }

    private class SpiDigestAlgorithmFactory(
        schemeMetadata: CipherSchemeMetadata,
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
                throw IllegalArgumentException("Unknown hash algorithm $algorithm")
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
