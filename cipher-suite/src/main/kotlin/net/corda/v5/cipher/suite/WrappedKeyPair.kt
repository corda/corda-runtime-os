package net.corda.v5.cipher.suite

import java.security.PublicKey

/**
 * Holding class for the returned by teh CryptoService wrapped key pair.
 */
class WrappedKeyPair(val publicKey: PublicKey, val keyMaterial: ByteArray, val encodingVersion: Int)
