package net.corda.packaging.tests.legacy

import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

// WARNING - "legacy" corda5 code *only* used to make tests pass.
sealed class DigestAlgorithmFactoryImpl : DigestAlgorithmFactory {
    private class MessageDigestFactory(override val algorithm: String) : DigestAlgorithmFactory {
        override fun getInstance(): DigestAlgorithm {
            try {
                val messageDigest = MessageDigest.getInstance(algorithm)
                return MessageDigestWrapper(messageDigest, algorithm)
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalArgumentException("Unknown hash algorithm $algorithm")
            }
        }

        private class MessageDigestWrapper(val messageDigest: MessageDigest, override val algorithm: String) :
            DigestAlgorithm {
            override val digestLength = messageDigest.digestLength
            override fun digest(bytes: ByteArray): ByteArray = messageDigest.digest(bytes)
        }
    }

    companion object {
        private const val SHA2_256 = "SHA-256"

        private val BANNED: Set<String> = Collections.unmodifiableSet(setOf("MD5", "MD2", "SHA-1"))
        private val sha256Factory = MessageDigestFactory(SHA2_256)
        private val factories = ConcurrentHashMap<String, DigestAlgorithmFactory>()

        private fun check(algorithm: String) {
            require(algorithm.toUpperCase() == algorithm) { "Hash algorithm name $this must be in the upper case" }
            require(algorithm !in BANNED) { "$algorithm is forbidden!" }
        }

        fun getInstance(algorithm: String): DigestAlgorithm {
            check(algorithm)
            return when (algorithm) {
                SHA2_256 -> sha256Factory.getInstance()
                else -> factories[algorithm]?.getInstance() ?: MessageDigestFactory(algorithm).getInstance()
            }
        }
    }
}
