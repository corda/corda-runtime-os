package net.corda.crypto

import net.corda.v5.crypto.OID_ALIAS_PRIVATE_KEY_IDENTIFIER
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.KeyPair
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Decode a PKCS8 encoded key to its [PrivateKey] object.
 * Use this method if the key type is a-priori unknown.
 * @param encodedKey a PKCS8 encoded private key.
 * @throws IllegalArgumentException on not supported scheme or if the given key specification
 * is inappropriate for this key factory to produce a private key.
 */
fun decodeAliasPrivateKey(encodedKey: ByteArray): PrivateKey {
    val keyInfo = PrivateKeyInfo.getInstance(encodedKey)
    if (keyInfo.privateKeyAlgorithm.algorithm == OID_ALIAS_PRIVATE_KEY_IDENTIFIER) {
        val encodable = keyInfo.parsePrivateKey() as DLSequence
        val derutF8String = encodable.getObjectAt(0)
        val alias = (derutF8String as DERUTF8String).string
        return AliasPrivateKey(alias)
    } else {
        throw IllegalArgumentException(
            "The key is not ${AliasPrivateKey::class.java.name}"
        )
    }
}

/**
 * [PrivateKey] wrapper to just store the alias of a private key.
 * Usually, HSM (hardware secure module) key entries are accessed via unique aliases and the private key material never
 * leaves the box. This class wraps a [String] key alias into a [PrivateKey] object, which helps on transferring
 * [KeyPair] objects without exposing the private key material. Then, whenever we need to sign with the actual private
 * key, we provide the [alias] from this [AliasPrivateKey] to the underlying HSM implementation.
 */
data class AliasPrivateKey(val alias: String) : PrivateKey {

    companion object {
        const val ALIAS_KEY_ALGORITHM = "AliasPrivateKey"
    }

    override fun getAlgorithm() = ALIAS_KEY_ALGORITHM

    override fun getEncoded(): ByteArray {
        val keyVector = ASN1EncodableVector()
        keyVector.add(DERUTF8String(alias))
        val privateKeyInfoBytes = PrivateKeyInfo(
            AlgorithmIdentifier(OID_ALIAS_PRIVATE_KEY_IDENTIFIER),
            DERSequence(keyVector)
        ).getEncoded(ASN1Encoding.DER)
        val keySpec = PKCS8EncodedKeySpec(privateKeyInfoBytes)
        return keySpec.encoded
    }

    override fun getFormat() = "PKCS#8"
}
