package net.corda.p2p.crypto.protocol

class ProtocolConstants {
    companion object {
        const val PROTOCOL_VERSION = 1

        const val CIPHER_ALGO = "AES/GCM/NoPadding"
        const val CIPHER_KEY_SIZE_BYTES = 32
        const val CIPHER_NONCE_SIZE_BYTES = 12

        const val HASH_ALGO = "SHA-256"

        const val HMAC_ALGO = "HMac-SHA256"
        const val HMAC_KEY_SIZE_BYTES = 32

        const val ELLIPTIC_CURVE_ALGO = "X25519"
        const val ELLIPTIC_CURVE_KEY_SIZE_BITS = 256


        val INITIATOR_SIG_PAD = " ".repeat(64) + "Corda, client signature verify" + "\\0"
        val RESPONDER_SIG_PAD = " ".repeat(64) + "Corda, server signature verify" + "\\0"

        const val INITIATOR_HANDSHAKE_ENCRYPTION_KEY_INFO = "Corda client hs enc key"
        const val RESPONDER_HANDSHAKE_ENCRYPTION_KEY_INFO = "Corda server hs enc key"
        const val INITIATOR_HANDSHAKE_ENCRYPTION_NONCE_INFO = "Corda client hs enc iv"
        const val RESPONDER_HANDSHAKE_ENCRYPTION_NONCE_INFO = "Corda server hs enc iv"
        const val INITIATOR_HANDSHAKE_MAC_KEY_INFO = "Corda client hs mac key"
        const val RESPONDER_HANDSHAKE_MAC_KEY_INFO = "Corda server hs mac key"

        const val INITIATOR_SESSION_ENCRYPTION_KEY_INFO = "Corda client session key"
        const val RESPONDER_SESSION_ENCRYPTION_KEY_INFO = "Corda server session key"
        const val INITIATOR_SESSION_NONCE_INFO = "Corda client session iv"
        const val RESPONDER_SESSION_NONCE_INFO = "Corda server session iv"

        /**
         * We establish a minimum packet size of 10KB to ensure all handshake messages are considered valid.
         */
        const val MIN_PACKET_SIZE = 10_000
    }
}