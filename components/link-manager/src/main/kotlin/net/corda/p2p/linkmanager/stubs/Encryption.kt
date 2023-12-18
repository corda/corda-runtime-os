package net.corda.p2p.linkmanager.stubs

import org.bouncycastle.util.encoders.Base64

/**
 * This is an unsafe encryption stub. It should be replaced.
 */
internal class Encryption {
    fun encrypt(data: ByteArray): ByteArray {
        return Base64.encode(data)
    }
    fun decrypt(data: ByteArray): ByteArray {
        return Base64.decode(data)
    }
}
