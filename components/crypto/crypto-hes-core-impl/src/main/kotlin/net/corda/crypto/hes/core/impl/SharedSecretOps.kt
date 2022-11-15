package net.corda.crypto.hes.core.impl

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.crypto.hes.AES_KEY_ALGORITHM
import net.corda.crypto.hes.AES_KEY_SIZE_BYTES
import net.corda.crypto.hes.AES_PROVIDER
import net.corda.crypto.hes.GCM_NONCE_LENGTH
import net.corda.crypto.hes.GCM_TAG_LENGTH
import net.corda.crypto.hes.GCM_TRANSFORMATION
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jcajce.provider.util.DigestFactory
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

object SharedSecretOps {
    private const val digestName = "SHA-256"
    private val AES_KEY_HKDF_INFO = "Corda key".toByteArray()
    private val AES_NONCE_HKDF_INFO = "Corda iv".toByteArray()

    private val pool = ConcurrentLinkedQueue<Cipher>()

    private fun withGcmCipherInstance(block: Cipher.() -> ByteArray): ByteArray {
        val cipher = pool.poll()
            ?: Cipher.getInstance(GCM_TRANSFORMATION, AES_PROVIDER)
        try {
            return cipher.block()
        } finally {
            pool.offer(cipher)
        }
    }

    @Suppress("LongParameterList")
    fun encrypt(
        salt: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        aad: ByteArray?,
        deriveSharedSecret: (PublicKey) -> ByteArray
    ): ByteArray {
        val currentNonceCounter = Instant.now().toEpochMilli().toByteArray()
        val data = derive(
            salt = salt,
            publicKey = publicKey,
            currentNonceCounter = currentNonceCounter,
            deriveSharedSecret = deriveSharedSecret
        )
        val finalAad = concatByteArrays(
            aad ?: ByteArray(0), publicKey.encoded, otherPublicKey.encoded
        )
        return withGcmCipherInstance {
            init(Cipher.ENCRYPT_MODE, data.key, GCMParameterSpec(GCM_TAG_LENGTH * 8, data.nonce))
            updateAAD(finalAad)
            concatByteArrays(currentNonceCounter, doFinal(plainText))
        }
    }

    @Suppress("LongParameterList")
    fun decrypt(
        salt: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?,
        deriveSharedSecret: (PublicKey) -> ByteArray
    ): ByteArray {
        val currentNonceCounter = cipherText.sliceArray(0 until Long.SIZE_BYTES)
        val data = derive(
            salt = salt,
            publicKey = publicKey,
            currentNonceCounter = currentNonceCounter,
            deriveSharedSecret = deriveSharedSecret
        )
        val finalAad = concatByteArrays(
            aad ?: ByteArray(0), otherPublicKey.encoded, publicKey.encoded
        )
        val cipherTextAndTag = cipherText.sliceArray(Long.SIZE_BYTES until cipherText.size)
        return withGcmCipherInstance {
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, data.nonce)
            init(Cipher.DECRYPT_MODE, data.key, spec)
            updateAAD(finalAad)
            doFinal(cipherTextAndTag)
        }
    }

    private fun derive(
        salt: ByteArray,
        publicKey: PublicKey,
        currentNonceCounter: ByteArray,
        deriveSharedSecret: (PublicKey) -> ByteArray
    ): DerivedData {
        require(salt.isNotEmpty()) {
            "The salt must not be empty"
        }
        val sharedSecret = deriveSharedSecret(publicKey)
        val okm = hkdf(sharedSecret, digestName, salt, AES_KEY_HKDF_INFO, AES_KEY_SIZE_BYTES)
        val nonce = hkdf(sharedSecret, digestName, salt, AES_NONCE_HKDF_INFO, GCM_NONCE_LENGTH).also {
            it[0] = it[0] xor currentNonceCounter[0]
            it[1] = it[1] xor currentNonceCounter[1]
            it[2] = it[2] xor currentNonceCounter[2]
            it[3] = it[3] xor currentNonceCounter[3]
            it[4] = it[4] xor currentNonceCounter[4]
            it[5] = it[5] xor currentNonceCounter[5]
            it[6] = it[6] xor currentNonceCounter[6]
            it[7] = it[7] xor currentNonceCounter[7]
        }
        return DerivedData(
            key = SecretKeySpec(okm, AES_KEY_ALGORITHM),
            nonce = nonce
        )
    }

    private fun hkdf(sharedSecret: ByteArray, digestName: String, salt: ByteArray, info: ByteArray, len: Int): ByteArray {
        val bytes = ByteArray(len)
        HKDFBytesGenerator(DigestFactory.getDigest(digestName)).apply {
            init(HKDFParameters(sharedSecret, salt, info))
            generateBytes(bytes, 0, bytes.size)
        }
        return bytes
    }

    class DerivedData(
        val key: SecretKeySpec,
        val nonce: ByteArray
    )
}