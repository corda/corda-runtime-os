package net.corda.p2p.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlin.concurrent.withLock
import kotlin.experimental.xor

class AuthenticatedSession(private val outboundSecretKey: SecretKey, private val outboundNonce: ByteArray, private val inboundSecretKey: SecretKey, private val inboundNonce: ByteArray) {

    private val aesCipher = Cipher.getInstance("AES/GCM/NoPadding", BouncyCastleProvider())
    private val secureRandom = SecureRandom()
    //TODO: It is probably faster to use a Cipher instance per thread.
    private val lock = ReentrantLock()

    /**
     * @return  (in this order) the encrypted data, the authentication tag and the nonce.
     */
    fun encrypt(header: ByteArray, payload: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val messageNonce = ByteArray(outboundNonce.size)
        secureRandom.nextBytes(messageNonce)
        val finalNonce = messageNonce.mapIndexed { index, byte -> byte xor outboundNonce[index]  }.toByteArray()
        val (ciphertext, tag) =  lock.withLock {
            aesCipher.encryptWithAssociatedData(header, finalNonce, payload, outboundSecretKey)
        }
        return Triple(ciphertext, tag, messageNonce)
    }

    /**
     * @return the decrypted payload
     */
    fun decrypt(header: ByteArray, encryptedPayload: ByteArray, tag: ByteArray, messageNonce: ByteArray): ByteArray {
        val finalNonce = messageNonce.mapIndexed { index, byte -> byte xor inboundNonce[index] }.toByteArray()
        return lock.withLock { aesCipher.decrypt(header, tag, finalNonce, encryptedPayload, inboundSecretKey) }
    }

}