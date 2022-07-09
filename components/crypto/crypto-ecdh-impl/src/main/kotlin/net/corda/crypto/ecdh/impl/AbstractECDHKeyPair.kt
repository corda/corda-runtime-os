package net.corda.crypto.ecdh.impl

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.crypto.ecdh.AES_KEY_ALGORITHM
import net.corda.crypto.ecdh.AES_KEY_SIZE_BYTES
import net.corda.crypto.ecdh.AES_PROVIDER
import net.corda.crypto.ecdh.ECDHKeyPair
import net.corda.crypto.ecdh.GCM_NONCE_LENGTH
import net.corda.crypto.ecdh.GCM_TAG_LENGTH
import net.corda.crypto.ecdh.GCM_TRANSFORMATION
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jcajce.provider.util.DigestFactory
import java.security.PublicKey
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

abstract class AbstractECDHKeyPair(
    override val publicKey: PublicKey,
    override val otherPublicKey: PublicKey,
    override val digestName: String
) : ECDHKeyPair {
    companion object {
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
    }

    private val nonceCounter = AtomicInteger(0)

    final override fun encrypt(
        salt: ByteArray,
        info: ByteArray,
        plainText: ByteArray,
        aad: ByteArray?
    ): ByteArray {
        val currentNonceCounter = nonceCounter.getAndIncrement().toByteArray()
        val data = derive(
            salt = salt,
            info = info,
            currentNonceCounter = currentNonceCounter,
            aad = concatByteArrays(
                aad ?: ByteArray(0), publicKey.encoded, otherPublicKey.encoded
            )
        )
        return withGcmCipherInstance {
            init(Cipher.ENCRYPT_MODE, data.key, GCMParameterSpec(GCM_TAG_LENGTH * 8, data.nonce))
            updateAAD(data.aad)
            concatByteArrays(currentNonceCounter, doFinal(plainText))
        }
    }

    final override fun decrypt(
        salt: ByteArray,
        info: ByteArray,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray {
        val currentNonceCounter = cipherText.sliceArray(0 until GCM_NONCE_LENGTH)
        val data = derive(
            salt = salt,
            info = info,
            currentNonceCounter = currentNonceCounter,
            aad = concatByteArrays(
                aad ?: ByteArray(0), otherPublicKey.encoded, publicKey.encoded
            )
        )
        return withGcmCipherInstance {
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, data.nonce)
            init(Cipher.DECRYPT_MODE, data.key, spec)
            updateAAD(data.aad)
            doFinal(cipherText)
        }
    }

    private fun derive(
        salt: ByteArray,
        info: ByteArray,
        currentNonceCounter: ByteArray,
        aad: ByteArray
    ): DerivedData {
        require(salt.isNotEmpty()) {
            "The salt must not be empty"
        }
        require(info.isNotEmpty()) {
            "The info must not be empty"
        }
        val okm = ByteArray(AES_KEY_SIZE_BYTES + GCM_NONCE_LENGTH)
        HKDFBytesGenerator(DigestFactory.getDigest(digestName)).apply {
            init(HKDFParameters(deriveSharedSecret(), salt, info))
            generateBytes(okm, 0, okm.size)
        }
        return DerivedData(
            key = SecretKeySpec(okm.sliceArray(0 until AES_KEY_SIZE_BYTES), AES_KEY_ALGORITHM),
            nonce = okm.sliceArray(AES_KEY_SIZE_BYTES until okm.size).also {
                it[0] = it[0] xor currentNonceCounter[0]
                it[1] = it[1] xor currentNonceCounter[1]
                it[2] = it[2] xor currentNonceCounter[2]
                it[3] = it[3] xor currentNonceCounter[3]
            },
            aad = aad
        )
    }

    abstract fun deriveSharedSecret(): ByteArray

    class DerivedData(
        val key: SecretKeySpec,
        val nonce: ByteArray,
        val aad: ByteArray
    )
}