package net.corda.crypto.ecdh.impl

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.crypto.ecdh.AES_KEY_ALGORITHM
import net.corda.crypto.ecdh.AES_KEY_SIZE_BYTES
import net.corda.crypto.ecdh.AES_PROVIDER
import net.corda.crypto.ecdh.GCM_NONCE_LENGTH
import net.corda.crypto.ecdh.GCM_TAG_LENGTH
import net.corda.crypto.ecdh.GCM_TRANSFORMATION
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jcajce.provider.util.DigestFactory
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

object SharedSecretOps {
    private val nonceCounter = AtomicInteger(0)

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

    fun encrypt(
        digestName: String,
        salt: ByteArray,
        info: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        aad: ByteArray?,
        deriveSharedSecret: (PublicKey) -> ByteArray
    ): ByteArray {
        val currentNonceCounter = nonceCounter.getAndIncrement().toByteArray()
        val data = derive(
            digestName = digestName,
            salt = salt,
            info = info,
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

    fun decrypt(
        digestName: String,
        salt: ByteArray,
        info: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?,
        deriveSharedSecret: (PublicKey) -> ByteArray
    ): ByteArray {
        val currentNonceCounter = cipherText.sliceArray(0 until GCM_NONCE_LENGTH)
        val data = derive(
            digestName = digestName,
            salt = salt,
            info = info,
            publicKey = publicKey,
            currentNonceCounter = currentNonceCounter,
            deriveSharedSecret = deriveSharedSecret
        )
        val finalAad = concatByteArrays(
            aad ?: ByteArray(0), otherPublicKey.encoded, publicKey.encoded
        )
        val cipherTextAndTag = cipherText.sliceArray(Int.SIZE_BYTES until cipherText.size)
        return withGcmCipherInstance {
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, data.nonce)
            init(Cipher.DECRYPT_MODE, data.key, spec)
            updateAAD(finalAad)
            doFinal(cipherTextAndTag)
        }
    }

    private fun derive(
        digestName: String,
        salt: ByteArray,
        info: ByteArray,
        publicKey: PublicKey,
        currentNonceCounter: ByteArray,
        deriveSharedSecret: (PublicKey) -> ByteArray
    ): DerivedData {
        require(salt.isNotEmpty()) {
            "The salt must not be empty"
        }
        require(info.isNotEmpty()) {
            "The info must not be empty"
        }
        val okm = ByteArray(AES_KEY_SIZE_BYTES + GCM_NONCE_LENGTH)
        HKDFBytesGenerator(DigestFactory.getDigest(digestName)).apply {
            init(HKDFParameters(deriveSharedSecret(publicKey), salt, info))
            generateBytes(okm, 0, okm.size)
        }
        return DerivedData(
            key = SecretKeySpec(okm.sliceArray(0 until AES_KEY_SIZE_BYTES), AES_KEY_ALGORITHM),
            nonce = okm.sliceArray(AES_KEY_SIZE_BYTES until okm.size).also {
                it[0] = it[0] xor currentNonceCounter[0]
                it[1] = it[1] xor currentNonceCounter[1]
                it[2] = it[2] xor currentNonceCounter[2]
                it[3] = it[3] xor currentNonceCounter[3]
            }
        )
    }

    internal fun deriveSharedSecret(provider: Provider, privateKey: PrivateKey, otherPublicKey: PublicKey): ByteArray {
        require(otherPublicKey.algorithm == privateKey.algorithm) {
            "Keys must use the same algorithm"
        }
        return when (privateKey.algorithm) {
            "EC" -> {
                KeyAgreement.getInstance("ECDH", provider).apply {
                    init(privateKey)
                    doPhase(otherPublicKey, true)
                }.generateSecret()
            }
            "X25519" -> {
                KeyAgreement.getInstance("X25519", provider).apply {
                    init(privateKey)
                    doPhase(otherPublicKey, true)
                }.generateSecret()
            }
            else -> throw IllegalArgumentException("Can't handle algorithm ${privateKey.algorithm}")
        }
    }

    class DerivedData(
        val key: SecretKeySpec,
        val nonce: ByteArray
    )
}