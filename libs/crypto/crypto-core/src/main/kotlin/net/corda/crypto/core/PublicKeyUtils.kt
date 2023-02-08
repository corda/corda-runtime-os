package net.corda.crypto.core

import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.sha256Bytes
import java.security.PublicKey

// TODO we should end up with only having helpers all of them using `DigestService`/ `PlatformDigestService`
/**
 * Returns the id as the first 12 characters of an SHA-256 hash of the public key.
 */
fun publicKeyIdFromBytes(publicKey: ByteArray): String =
    PublicKeyHash.calculate(publicKey).id

fun fullPublicKeyIdFromBytes(publicKey: ByteArray, digestService: PlatformDigestService): String =
    // TODO default digest algorithm needs to selected through default digest service
    //  fow now this just uses same algorithm as `publicKeyIdFromBytes`
    digestService.hash(publicKey, DigestAlgorithmName.SHA2_256).toString()

// TODO Remove the followings, only adding now for convenience
fun fullPublicKeyIdFromBytes(publicKey: ByteArray): String =
    SecureHash(DigestAlgorithmName.SHA2_256.name, publicKey.sha256Bytes()).toString()

fun PublicKey.fullId(): String =
    fullPublicKeyIdFromBytes(this.encoded)
