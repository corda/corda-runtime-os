package net.corda.p2p.linkmanager.stubs

import javax.crypto.Cipher
import javax.crypto.KeyGenerator

/**
 * This is an unsafe encryption stub. It should be replaced.
 */
internal class Encryption {
    private companion object {
        const val ALGORITHM = "AES"
    }
    private val key by lazy {
        val key = KeyGenerator.getInstance(ALGORITHM)
        key.init(128)
        key.generateKey()
    }

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }
    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key)
        return cipher.doFinal(data)
    }
}
