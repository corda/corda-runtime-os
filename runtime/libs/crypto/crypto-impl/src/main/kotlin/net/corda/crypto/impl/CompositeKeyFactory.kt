package net.corda.crypto.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyFactorySpi
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Factory for generating composite keys from ASN.1 format key specifications. This is used by [CordaSecurityProvider],
 * and only by that.
 *
 * In Java Security, the only things that should be directly serialising and deserialising keys are these methods;
 * everything else should go via
 *
 * @property keyEncoder the key decoder which is sued to decode the public keys comprising the composite key.
 */
class CompositeKeyFactory(
    private val keyEncoder: KeyEncodingService
) : KeyFactorySpi() {

    @Throws(InvalidKeySpecException::class)
    override fun engineGeneratePrivate(keySpec: KeySpec): PrivateKey {
        // Private composite key not supported.
        throw InvalidKeySpecException("key spec not recognised: " + keySpec.javaClass)
    }

    @Throws(InvalidKeySpecException::class)
    override fun engineGeneratePublic(keySpec: KeySpec): PublicKey = when (keySpec) {
        is X509EncodedKeySpec -> createFromASN1(keySpec.encoded)
        else -> throw InvalidKeySpecException("key spec not recognised: " + keySpec.javaClass)
    }

    @Throws(InvalidKeySpecException::class)
    override fun <T : KeySpec> engineGetKeySpec(key: Key, keySpec: Class<T>): T {
        // Only support X509EncodedKeySpec.
        throw InvalidKeySpecException("Not implemented yet $key $keySpec")
    }

    @Throws(InvalidKeyException::class)
    override fun engineTranslateKey(key: Key): Key {
        throw InvalidKeyException("No other composite key providers known")
    }

    private fun createFromASN1(asn1: ByteArray): PublicKey {
        val asn1Obj = ASN1Primitive.fromByteArray(asn1)
        val keyInfo = SubjectPublicKeyInfo.getInstance(asn1Obj)
        require(keyInfo != null) { "Key must be non-null" }
        require(keyInfo.algorithm.algorithm == OID_COMPOSITE_KEY_IDENTIFIER) { "Key must be composite" }
        val sequence = ASN1Sequence.getInstance(keyInfo.parsePublicKey())
        val threshold = ASN1Integer.getInstance(sequence.getObjectAt(0))
        require(threshold == null || threshold.positiveValue.toInt() > 0) {
            "Threshold must not be specified or its value must be greater than zero"
        }
        val sequenceOfChildren = ASN1Sequence.getInstance(sequence.getObjectAt(1))
        val listOfChildren = sequenceOfChildren.objects.toList()

        val keys = listOfChildren.map {
            require(it is ASN1Sequence) { "Child key is not in ASN1 format" }
            CompositeKeyNodeAndWeight(
                keyEncoder.decodePublicKey((it.getObjectAt(0) as DERBitString).bytes),
                ASN1Integer.getInstance(it.getObjectAt(1)).positiveValue.toInt()
            )
        }
        return CompositeKeyProviderImpl().create(keys, threshold?.positiveValue?.toInt())
    }


}
