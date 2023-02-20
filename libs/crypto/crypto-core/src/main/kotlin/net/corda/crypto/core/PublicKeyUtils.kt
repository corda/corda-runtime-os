package net.corda.crypto.core

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.sha256Bytes
import java.security.PublicKey

// TODO we should end up with only having helpers all of them using `DigestService`/ `PlatformDigestService`
//  as recorded in https://r3-cev.atlassian.net/browse/CORE-10267.
/**
 * Returns the id as the first 12 characters of an SHA-256 hash of the public key.
 */
// TODO Should use `digestService`
fun publicKeyIdFromBytes(publicKey: ByteArray): String =
    // TODO Need to replace below calculation with using `DigestService` and get short id from its outcome
    PublicKeyHash.calculate(publicKey).id

fun fullPublicKeyIdFromBytes(publicKey: ByteArray, digestService: PlatformDigestService): String =
    // TODO default digest algorithm needs to selected through default digest service
    //  for now this just uses same algorithm as `publicKeyIdFromBytes`
    digestService.hash(publicKey, DigestAlgorithmName.SHA2_256).toString()

fun PublicKey.fullId(keyEncodingService: KeyEncodingService, digestService: PlatformDigestService): String =
    fullPublicKeyIdFromBytes(keyEncodingService.encodeAsByteArray(this), digestService)

// TODO Remove the followings, only adding now for convenience
fun fullPublicKeyIdFromBytes(publicKey: ByteArray): String =
    SecureHash(DigestAlgorithmName.SHA2_256.name, publicKey.sha256Bytes()).toString()

fun PublicKey.fullId(): String =
    fullPublicKeyIdFromBytes(this.encoded)
