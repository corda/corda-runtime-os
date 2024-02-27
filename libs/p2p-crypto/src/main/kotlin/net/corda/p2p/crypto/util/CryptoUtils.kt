package net.corda.p2p.crypto.util

import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.HASH_ALGO
import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.lang.UnsupportedOperationException
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * @return the decrypted data
 */
internal fun Cipher.decrypt(aad: ByteArray, tag: ByteArray, nonce: ByteArray, ciphertext: ByteArray, secretKey: SecretKey): ByteArray {
    synchronized(this) {
        this.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
        this.updateAAD(aad)
        return this.doFinal(ciphertext + tag)
    }
}

/**
 * @return (in this order) the encrypted data and the authentication tag
 */
internal fun Cipher.encryptWithAssociatedData(
    aad: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    secretKey: SecretKey,
): Pair<ByteArray, ByteArray> {
    synchronized(this) {
        this.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
        this.updateAAD(aad)
        val cipherWithTag = this.doFinal(plaintext)
        val cipher = cipherWithTag.copyOfRange(0, cipherWithTag.size - nonce.size)
        val tag = cipherWithTag.copyOfRange(cipherWithTag.size - nonce.size, cipherWithTag.size)
        return Pair(cipher, tag)
    }
}

internal fun Signature.verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
    synchronized(this) {
        this.initVerify(publicKey)
        this.update(data)
        return this.verify(signature)
    }
}

/**
 * @return the shared secret key as a byte array.
 */
internal fun KeyAgreement.perform(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
    synchronized(this) {
        this.init(privateKey)
        this.doPhase(publicKey, true)
        return this.generateSecret()
    }
}

internal fun MessageDigest.hash(data: ByteArray): ByteArray {
    synchronized(this) {
        this.reset()
        this.update(data)
        return digest()
    }
}

internal fun Mac.calculateMac(key: SecretKey, data: ByteArray): ByteArray {
    synchronized(this) {
        this.init(key)
        this.update(data)
        return this.doFinal()
    }
}

internal fun HKDFBytesGenerator.generateKey(salt: ByteArray, inputKeyMaterial: ByteArray, info: String, length: Int): ByteArray {
    synchronized(this) {
        this.init(HKDFParameters(inputKeyMaterial, salt, info.toByteArray(Charsets.UTF_8)))

        val outputKeyMaterial = ByteArray(length)
        this.generateBytes(outputKeyMaterial, 0, length)

        return outputKeyMaterial
    }
}

internal fun MessageDigest.convertToBCDigest(): Digest {
    return when (this.algorithm) {
        HASH_ALGO -> SHA256Digest()
        else -> throw UnsupportedOperationException("Conversion not supported for algorithm: ${this.algorithm}")
    }
}
