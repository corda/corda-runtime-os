package net.corda.crypto.hes.core.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.hes.CryptoUnsafeHESKeyException
import net.corda.crypto.hes.EncryptedDataWithKey
import net.corda.crypto.hes.HybridEncryptionParamsProvider
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import javax.crypto.KeyAgreement

fun deriveDHSharedSecret(provider: Provider, privateKey: PrivateKey, otherPublicKey: PublicKey): ByteArray {
    require(otherPublicKey.algorithm == privateKey.algorithm) {
        "Keys must use the same algorithm"
    }
    return when (privateKey.algorithm) {
        "EC" -> {
            KeyAgreement.getInstance("ECDH", provider).apply {
                init(privateKey)
                doPhase(otherPublicKey, true)
            }.generateSecret()
        }
        "X25519" -> {
            KeyAgreement.getInstance("X25519", provider).apply {
                init(privateKey)
                doPhase(otherPublicKey, true)
            }.generateSecret()
        }
        else -> throw IllegalArgumentException("Can't handle algorithm ${privateKey.algorithm}")
    }
}

/**
 * Check if a point's coordinates are on the expected curve to avoid certain types of ECC attacks.
 * Point-at-infinity is not permitted as well.
 * @see <a href="https://safecurves.cr.yp.to/twist.html">Small subgroup and invalid-curve attacks</a> for a more
 * descriptive explanation on such attacks.
 * We should note that we are doing it out of an abundance of caution and specifically to proactively protect developers
 * against using these points as part of a DH key agreement or for use cases as yet unimagined.
 * This method currently applies to BouncyCastle's ECDSA (both R1 and K1 curves).
 * @param publicKey a [PublicKey], usually used to validate a signer's public key in on the Curve.
 * @param scheme a [KeyScheme] object, retrieved from supported signature schemes.
 * @return true if the point lies on the curve or false if it doesn't.
 * @throws IllegalArgumentException if the requested signature scheme or the key type is not supported.
 */
fun publicKeyOnCurve(scheme: KeyScheme, publicKey: PublicKey) {
    val result = when (publicKey) {
        is BCECPublicKey -> publicKey.parameters == scheme.algSpec && !publicKey.q.isInfinity && publicKey.q.isValid
        else -> true
    }
    if(!result) {
        throw CryptoUnsafeHESKeyException("The key pair is not safe, publicKey=${publicKey.publicKeyId()}:$scheme")
    }
}

fun encryptWithEphemeralKeyPair(
    schemeMetadata: CipherSchemeMetadata,
    otherPublicKey: PublicKey,
    plainText: ByteArray,
    params: HybridEncryptionParamsProvider
): EncryptedDataWithKey {
    val scheme = schemeMetadata.findKeyScheme(otherPublicKey)
    val provider = schemeMetadata.providers.getValue(scheme.providerName)
    val keyPair = generateEphemeralKeyPair(schemeMetadata, provider, scheme)
    publicKeyOnCurve(scheme, keyPair.public)
    publicKeyOnCurve(scheme, otherPublicKey)
    val paramValues = params.get(keyPair.public, otherPublicKey)
    val cipherText = SharedSecretOps.encrypt(
        salt = paramValues.salt,
        publicKey = keyPair.public,
        otherPublicKey = otherPublicKey,
        plainText = plainText,
        aad = paramValues.aad
    ) {
        deriveDHSharedSecret(provider, keyPair.private, otherPublicKey)
    }
    return EncryptedDataWithKey(
        publicKey = keyPair.public,
        params = paramValues,
        cipherText = cipherText
    )
}

@Suppress("LongParameterList")
fun decryptWithStableKeyPair(
    schemeMetadata: CipherSchemeMetadata,
    salt: ByteArray,
    publicKey: PublicKey,
    otherPublicKey: PublicKey,
    cipherText: ByteArray,
    aad: ByteArray?,
    deriveSharedSecret: (PublicKey) -> ByteArray
): ByteArray {
    val publicKeyScheme = schemeMetadata.findKeyScheme(publicKey)
    val otherPublicKeyScheme = schemeMetadata.findKeyScheme(otherPublicKey)
    require(publicKeyScheme == otherPublicKeyScheme) {
        "The keys must use the same key scheme, publicKey=$publicKeyScheme, otherPublicKey=$otherPublicKeyScheme"
    }
    publicKeyOnCurve(publicKeyScheme, publicKey)
    publicKeyOnCurve(otherPublicKeyScheme, otherPublicKey)
    return SharedSecretOps.decrypt(
        salt = salt,
        publicKey = publicKey,
        otherPublicKey = otherPublicKey,
        cipherText = cipherText,
        aad = aad,
        deriveSharedSecret = deriveSharedSecret
    )
}

private fun generateEphemeralKeyPair(
    schemeMetadata: CipherSchemeMetadata,
    provider: Provider,
    scheme: KeyScheme
): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance(
        scheme.algorithmName,
        provider
    )
    if (scheme.algSpec != null) {
        keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
    } else if (scheme.keySize != null) {
        keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
    }
    return keyPairGenerator.generateKeyPair()
}