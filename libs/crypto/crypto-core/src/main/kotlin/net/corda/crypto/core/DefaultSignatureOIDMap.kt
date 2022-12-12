package net.corda.crypto.core

import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.equal
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256K1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.ID_CURVE_25519PH
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.SHA512_256
import net.corda.crypto.cipher.suite.schemes.SM2_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.SPHINCS256_TEMPLATE

import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.bc.BCObjectIdentifiers
import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers
import org.bouncycastle.asn1.gm.GMObjectIdentifiers
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.RSASSAPSSparams
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import java.security.PublicKey

object DefaultSignatureOIDMap {
    val EDDSA_ED25519 = AlgorithmIdentifier(ID_CURVE_25519PH, null)

    val SPHINCS256_SHA512 = AlgorithmIdentifier(
        BCObjectIdentifiers.sphincs256_with_SHA512,
        DLSequence(arrayOf(ASN1Integer(0), SHA512_256))
    )

    val SM3_SM2 = AlgorithmIdentifier(
        GMObjectIdentifiers.sm2sign_with_sm3,
        GMObjectIdentifiers.sm2p256v1
    )

    val SM3_SHA256 = AlgorithmIdentifier(
        GMObjectIdentifiers.sm2sign_with_sha256,
        GMObjectIdentifiers.sm2p256v1
    )

    val GOST3410_GOST3411 = AlgorithmIdentifier(
        CryptoProObjectIdentifiers.gostR3410_94, DLSequence(
            arrayOf(
                CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A,
                CryptoProObjectIdentifiers.gostR3411_94_CryptoProParamSet
            )
        )
    )

    val SHA256_ECDSA_R1 =
        AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256r1)

    val SHA384_ECDSA_R1 =
        AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA384, SECObjectIdentifiers.secp256r1)

    val SHA512_ECDSA_R1 =
        AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA512, SECObjectIdentifiers.secp256r1)

    val SHA256_ECDSA_K1 =
        AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256k1)

    val SHA384_ECDSA_K1 =
        AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA384, SECObjectIdentifiers.secp256k1)

    val SHA512_ECDSA_K1 =
        AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA512, SECObjectIdentifiers.secp256k1)

    val SHA256_RSA = AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, null)

    val SHA384_RSA = AlgorithmIdentifier(PKCSObjectIdentifiers.sha384WithRSAEncryption, null)

    val SHA512_RSA = AlgorithmIdentifier(PKCSObjectIdentifiers.sha512WithRSAEncryption, null)

    val SHA256_RSASSA_PSS = AlgorithmIdentifier(
        PKCSObjectIdentifiers.id_RSASSA_PSS, createPSSParams(
            hashAlgId = AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE),
            saltSize = 32
        )
    )

    val SHA384_RSASSA_PSS = AlgorithmIdentifier(
        PKCSObjectIdentifiers.id_RSASSA_PSS, createPSSParams(
            hashAlgId = AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384, DERNull.INSTANCE),
            saltSize = 48
        )
    )

    val SHA512_RSASSA_PSS = AlgorithmIdentifier(
        PKCSObjectIdentifiers.id_RSASSA_PSS, createPSSParams(
            hashAlgId = AlgorithmIdentifier(NISTObjectIdentifiers.id_sha512, DERNull.INSTANCE),
            saltSize = 64
        )
    )

    @Suppress("ComplexMethod", "NestedBlockDepth")
    fun inferSignatureOID(publicKey: PublicKey, signatureSpec: SignatureSpec): AlgorithmIdentifier? {
        if (signatureSpec is CustomSignatureSpec) {
            return null
        }
        val keyInfo = try {
            SubjectPublicKeyInfo.getInstance(publicKey.encoded)
        } catch (e: Throwable) {
            null
        } ?: return null
        val algorithm = normaliseAlgorithmIdentifier(keyInfo.algorithm)
        return if (EDDSA_ED25519_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.equal(SignatureSpec.EDDSA_ED25519)) {
                EDDSA_ED25519
            } else {
                null
            }
        } else if (SPHINCS256_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.equal(SignatureSpec.SPHINCS256_SHA512)) {
                SPHINCS256_SHA512
            } else {
                null
            }
        } else if (SM2_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            when {
                signatureSpec.equal(SignatureSpec.SM2_SM3) -> SM3_SM2
                signatureSpec.equal(SignatureSpec.SM2_SHA256) -> SM3_SHA256
                else -> null
            }
        } else if (GOST3410_GOST3411_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.equal(SignatureSpec.GOST3410_GOST3411)) {
                GOST3410_GOST3411
            } else {
                null
            }
        } else if (ECDSA_SECP256R1_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            when {
                signatureSpec.equal(SignatureSpec.ECDSA_SHA256) -> SHA256_ECDSA_R1
                signatureSpec.equal(SignatureSpec.ECDSA_SHA384) -> SHA384_ECDSA_R1
                signatureSpec.equal(SignatureSpec.ECDSA_SHA512) -> SHA512_ECDSA_R1
                else -> null
            }
        } else if (ECDSA_SECP256K1_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            when {
                signatureSpec.equal(SignatureSpec.ECDSA_SHA256) -> SHA256_ECDSA_K1
                signatureSpec.equal(SignatureSpec.ECDSA_SHA384) -> SHA384_ECDSA_K1
                signatureSpec.equal(SignatureSpec.ECDSA_SHA512) -> SHA512_ECDSA_K1
                else -> null
            }
        } else if (RSA_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.equal(SignatureSpec.RSA_SHA256)) {
                SHA256_RSA
            } else if (signatureSpec.equal(SignatureSpec.RSA_SHA384)) {
                SHA384_RSA
            } else if (signatureSpec.equal(SignatureSpec.RSA_SHA512)) {
                SHA512_RSA
            } else if (signatureSpec.equal(SignatureSpec.RSASSA_PSS_SHA256) ||
                signatureSpec.equal(SignatureSpec.RSA_SHA256_WITH_MGF1)
            ) {
                SHA256_RSASSA_PSS
            } else if (signatureSpec.equal(SignatureSpec.RSASSA_PSS_SHA384) ||
                signatureSpec.equal(SignatureSpec.RSA_SHA384_WITH_MGF1)
            ) {
                SHA384_RSASSA_PSS
            } else if (signatureSpec.equal(SignatureSpec.RSASSA_PSS_SHA512) ||
                signatureSpec.equal(SignatureSpec.RSA_SHA512_WITH_MGF1)
            ) {
                SHA512_RSASSA_PSS
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier =
        if (id.parameters is DERNull) {
            AlgorithmIdentifier(id.algorithm, null)
        } else {
            id
        }

    private fun createPSSParams(hashAlgId: AlgorithmIdentifier, saltSize: Int): RSASSAPSSparams = RSASSAPSSparams(
        hashAlgId,
        AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, hashAlgId),
        ASN1Integer(saltSize.toLong()),
        ASN1Integer(1)
    )
}
