package net.corda.p2p.linkmanager.stubs

import org.bouncycastle.util.encoders.Base64

/**
 * This is an unsafe encryption stub.
 * This will be replaced by proper encryption as part of CORE-18791.
 */
internal class Encryption {
    fun encrypt(data: ByteArray): ByteArray {
        return Base64.encode(data)
    }
    fun decrypt(data: ByteArray): ByteArray {
        return Base64.decode(data)
    }
}
