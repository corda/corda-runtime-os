package net.corda.p2p.linkmanager

import net.corda.p2p.crypto.protocol.ProtocolConstants
import java.security.Key
import java.security.MessageDigest

internal class KeyHasher {
    internal fun hash(key: Key): ByteArray {
        val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO)
        return messageDigest.digest(key.encoded)
    }
}
