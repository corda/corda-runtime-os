package net.corda.crypto.core

import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ID_CURVE_25519PH
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SHA512_256
import net.corda.v5.cipher.suite.schemes.SM2_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SPHINCS256_TEMPLATE
import net.corda.v5.crypto.EDDSA_ED25519_NONE_SIGNATURE_SPEC
import net.corda.v5.crypto.GOST3410_GOST3411_SIGNATURE_SPEC
import net.corda.v5.crypto.SPHINCS256_SHA512_SIGNATURE_SPEC
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
import java.security.spec.PSSParameterSpec

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

    val SM3_SHA384 = AlgorithmIdentifier(
        GMObjectIdentifiers.sm2sign_with_sha384,
        GMObjectIdentifiers.sm2p256v1
    )

    val SM3_SHA512 = AlgorithmIdentifier(
        GMObjectIdentifiers.sm2sign_with_sha512,
        GMObjectIdentifiers.sm2p256v1
    )

    val SM3_WHIRPOOL = AlgorithmIdentifier(
        GMObjectIdentifiers.sm2sign_with_whirlpool,
        GMObjectIdentifiers.sm2p256v1
    )

    val SM3_BLAKE2B256 = AlgorithmIdentifier(
        GMObjectIdentifiers.sm2sign_with_blake2s256,
        GMObjectIdentifiers.sm2p256v1
    )

    val SM3_BLAKE2B512 = AlgorithmIdentifier(
        GMObjectIdentifiers.sm2sign_with_blake2b512,
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

    private val SHA256_WITH_ECDSA = "SHA256withECDSA"
    private val SHA384_WITH_ECDSA = "SHA384withECDSA"
    private val SHA512_WITH_ECDSA = "SHA512withECDSA"

    private val SHA256_WITH_RSA = "SHA256withRSA"
    private val SHA384_WITH_RSA = "SHA384withRSA"
    private val SHA512_WITH_RSA = "SHA512withRSA"
    private val RSASSA_PSS = "RSASSA-PSS"
    private val SHA256_WITH_RSA_AND_MGF1 = "SHA256WITHRSAANDMGF1"
    private val SHA384_WITH_RSA_AND_MGF1 = "SHA384WITHRSAANDMGF1"
    private val SHA512_WITH_RSA_AND_MGF1 = "SHA512WITHRSAANDMGF1"

    @Suppress("ComplexMethod")
    fun inferSignatureOID(publicKey: PublicKey, signatureSpec: SignatureSpec): AlgorithmIdentifier? {
        if(signatureSpec.precalculateHash) {
            return null
        }
        val keyInfo = try {
            SubjectPublicKeyInfo.getInstance(publicKey.encoded)
        } catch (e: Throwable) {
            null
        } ?: return null
        val algorithm = normaliseAlgorithmIdentifier(keyInfo.algorithm)
        return if (EDDSA_ED25519_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.has(EDDSA_ED25519_NONE_SIGNATURE_SPEC.signatureName)) {
                EDDSA_ED25519
            } else {
                null
            }
        } else if (SPHINCS256_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.has(SPHINCS256_SHA512_SIGNATURE_SPEC.signatureName)) {
                SPHINCS256_SHA512
            } else {
                null
            }
        } else if (SM2_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            when(signatureSpec.signatureName.uppercase()) {
                "SM3WITHSM2" -> SM3_SM2
                "SHA256WITHSM2" -> SM3_SHA256
                "SHA384WITHSM2" -> SM3_SHA384
                "SHA512WITHSM2" -> SM3_SHA512
                "WHIRLPOOLWITHSM2" -> SM3_WHIRPOOL
                "BLAKE2B256WITHSM2" -> SM3_BLAKE2B256
                "BLAKE2B512WITHSM2" -> SM3_BLAKE2B512
                else -> null
            }
        } else if (GOST3410_GOST3411_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.has(GOST3410_GOST3411_SIGNATURE_SPEC.signatureName)) {
                GOST3410_GOST3411
            } else {
                null
            }
        } else if (ECDSA_SECP256R1_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.has(SHA256_WITH_ECDSA)) {
                SHA256_ECDSA_R1
            } else if (signatureSpec.has(SHA384_WITH_ECDSA)) {
                SHA384_ECDSA_R1
            } else if (signatureSpec.has(SHA512_WITH_ECDSA)) {
                SHA512_ECDSA_R1
            } else {
                null
            }
        } else if (ECDSA_SECP256K1_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.has(SHA256_WITH_ECDSA)) {
                SHA256_ECDSA_K1
            } else if (signatureSpec.has(SHA384_WITH_ECDSA)) {
                SHA384_ECDSA_K1
            } else if (signatureSpec.has(SHA512_WITH_ECDSA)) {
                SHA512_ECDSA_K1
            } else {
                null
            }
        } else if (RSA_TEMPLATE.algorithmOIDs.contains(algorithm)) {
            if (signatureSpec.has(SHA256_WITH_RSA)) {
                SHA256_RSA
            } else if (signatureSpec.has(SHA384_WITH_RSA)) {
                SHA384_RSA
            } else if (signatureSpec.has(SHA512_WITH_RSA)) {
                SHA512_RSA
            } else if (signatureSpec.has(SHA256_WITH_RSA_AND_MGF1)) {
                SHA256_RSASSA_PSS
            } else if (signatureSpec.has(SHA384_WITH_RSA_AND_MGF1)) {
                SHA384_RSASSA_PSS
            } else if (signatureSpec.has(SHA512_WITH_RSA_AND_MGF1)) {
                SHA512_RSASSA_PSS
            } else if (signatureSpec.has(RSASSA_PSS)) {
                (signatureSpec.params as? PSSParameterSpec)?.let {
                    if (it.isSHA256()) {
                        SHA256_RSASSA_PSS
                    } else if (it.isSHA384()) {
                        SHA384_RSASSA_PSS
                    } else if (it.isSHA512()) {
                        SHA512_RSASSA_PSS
                    } else {
                        null
                    }
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun PSSParameterSpec.isSHA256() = digestAlgorithm.equals(
        "SHA-256",
        true
    ) && saltLength == 32 && mgfAlgorithm == "MGF1"

    private fun PSSParameterSpec.isSHA384() = digestAlgorithm.equals(
        "SHA-384",
        true
    ) && saltLength == 48 && mgfAlgorithm == "MGF1"

    private fun PSSParameterSpec.isSHA512() = digestAlgorithm.equals(
        "SHA-512",
        true
    ) && saltLength == 64 && mgfAlgorithm == "MGF1"

    private fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier =
        if (id.parameters is DERNull) {
            AlgorithmIdentifier(id.algorithm, null)
        } else {
            id
        }

    private fun SignatureSpec.has(pattern: String) = signatureName.equals(pattern, true)

    private fun createPSSParams(hashAlgId: AlgorithmIdentifier, saltSize: Int): RSASSAPSSparams = RSASSAPSSparams(
        hashAlgId,
        AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, hashAlgId),
        ASN1Integer(saltSize.toLong()),
        ASN1Integer(1)
    )
}
