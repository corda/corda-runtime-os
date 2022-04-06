package net.corda.crypto.core

import net.corda.v5.crypto.PublicKeyHash
import java.security.PublicKey

/**
 * Returns the id as the first 12 characters of an SHA-256 hash of the public key.
 */
fun publicKeyIdOf(publicKey: ByteArray): String =
    PublicKeyHash.calculate(publicKey).id

/**
 * Returns the id as the first 12 characters of an SHA-256 hash of the public key.
 */
fun publicKeyIdOf(publicKey: PublicKey): String =
    PublicKeyHash.calculate(publicKey).id

