package net.corda.crypto.core

import net.corda.v5.base.types.toHexString
import net.corda.v5.crypto.sha256Bytes
import java.security.PublicKey

/**
 * Returns the id as the first 12 characters of an SHA-256 hash of the public key.
 */
fun publicKeyIdOf(publicKey: ByteArray): String =
    hashOf(publicKey).substring(0, 12)

/**
 * Returns the id as the first 12 characters of an SHA-256 hash of the public key.
 */
fun publicKeyIdOf(publicKey: PublicKey): String =
    hashOf(publicKey).substring(0, 12)

/**
 * Returns the public key full hash.
 */
fun hashOf(publicKey: ByteArray): String =
    publicKey.sha256Bytes().toHexString()

/**
 * Returns the public key full hash.
 */
fun hashOf(publicKey: PublicKey): String =
    publicKey.encoded.sha256Bytes().toHexString()

