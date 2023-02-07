package net.corda.crypto.core

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.PublicKeyHash
import java.security.PublicKey

/**
 * Returns the id as the first 12 characters of an SHA-256 hash of the public key.
 */
// TODO needs fixing to use digestService and digest algorithm name
fun publicKeyIdFromBytes(publicKey: ByteArray): String =
    PublicKeyHash.calculate(publicKey).id

// TODO rename the following to publicKeyIdFromBytes when existing `publicKeyIdFromBytes` gets renamed to publicKeyShortIdFromBytes
fun publicKeyFullIdFromBytes(publicKey: ByteArray, digestService: PlatformDigestService): String =
    // TODO default digest algorithm needs to selected through default digest service
    digestService.hash(publicKey, DigestAlgorithmName.DEFAULT_ALGORITHM_NAME).toString()

// TODO rename following to id
fun PublicKey.fullId(keyEncodingService: KeyEncodingService, digestService: PlatformDigestService): String =
    publicKeyFullIdFromBytes(keyEncodingService.encodeAsByteArray(this), digestService)

// TODO Change body to use digestService and digest algorithm name
fun publicKeyShortIdFromBytes(publicKey: ByteArray): String =
    publicKeyIdFromBytes(publicKey)