package net.corda.crypto.core

import net.corda.v5.crypto.PublicKeyHash

/**
 * Returns the id as the first 12 characters of an SHA-256 hash of the public key.
 */
fun publicKeyIdFromBytes(publicKey: ByteArray): String =
    PublicKeyHash.calculate(publicKey).id


