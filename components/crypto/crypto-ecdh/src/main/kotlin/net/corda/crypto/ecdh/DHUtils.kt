package net.corda.crypto.ecdh

import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.publicKeyId
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import javax.crypto.KeyAgreement

fun deriveSharedSecret(provider: Provider, privateKey: PrivateKey, otherPublicKey: PublicKey): ByteArray {
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
 * @see <a href="https://safecurves.cr.yp.to/twist.html">Small subgroup and invalid-curve attacks</a> for a more descriptive explanation on such attacks.
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
    require(result) {
        "The key pair is not safe, publicKey=${publicKey.publicKeyId()}:$scheme"
    }
}