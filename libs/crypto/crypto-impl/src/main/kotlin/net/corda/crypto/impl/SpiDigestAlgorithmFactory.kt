package net.corda.crypto.impl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Provider

class SpiDigestAlgorithmFactory(
    schemeMetadata: CipherSchemeMetadata,
    override val algorithm: String,
) : DigestAlgorithmFactory {
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
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while(true) {
                val read = inputStream.read(buffer)
                if(read <= 0) break
                messageDigest.update(buffer, 0, read)
            }
            return messageDigest.digest()
        }
    }
}

