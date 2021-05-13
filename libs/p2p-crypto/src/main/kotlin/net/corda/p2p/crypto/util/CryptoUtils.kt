package net.corda.p2p.crypto.util

import org.bouncycastle.crypto.digests.SHA256Digest
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
fun Cipher.decrypt(aad: ByteArray, tag: ByteArray, nonce: ByteArray, ciphertext: ByteArray, secretKey: SecretKey): ByteArray {
    this.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
    this.updateAAD(aad)
    return this.doFinal(ciphertext + tag)
}

/**
 * @return  (in this order) the encrypted data and the authentication tag
 */
fun Cipher.encryptWithAssociatedData(aad: ByteArray, nonce: ByteArray, plaintext: ByteArray, secretKey: SecretKey): Pair<ByteArray, ByteArray> {
    this.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
    this.updateAAD(aad)
    val cipherWithTag = this.doFinal(plaintext)
    val cipher = cipherWithTag.copyOfRange(0, cipherWithTag.size - nonce.size)
    val tag = cipherWithTag.copyOfRange(cipherWithTag.size - nonce.size, cipherWithTag.size)
    return Pair(cipher, tag)
}

fun Signature.verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
    this.initVerify(publicKey)
    this.update(data)
    return this.verify(signature)
}

/**
 * @return the shared secret key as a byte array.
 */
fun KeyAgreement.perform(privateKey: PrivateKey, publicKey: PublicKey): ByteArray  {
    this.init(privateKey)
    this.doPhase(publicKey, true)
    return this.generateSecret()
}

fun SHA256Digest.hash(data: ByteArray): ByteArray {
    this.reset()
    this.update(data, 0, data.size)
    val hash = ByteArray(this.digestSize)
    this.doFinal(hash, 0)
    return hash
}

fun Mac.calculateMac(key: SecretKey, data: ByteArray): ByteArray {
    this.init(key)
    this.update(data)
    return this.doFinal()
}