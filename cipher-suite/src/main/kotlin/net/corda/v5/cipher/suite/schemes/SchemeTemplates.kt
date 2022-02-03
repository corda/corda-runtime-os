@file:JvmName("SchemeTemplates")

package net.corda.v5.cipher.suite.schemes

import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.v5.crypto.SignatureSpec
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.bc.BCObjectIdentifiers
import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers
import org.bouncycastle.asn1.gm.GMObjectIdentifiers
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.GOST3410ParameterSpec
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec

const val RSA_CODE_NAME = "CORDA.RSA"
const val ECDSA_SECP256K1_CODE_NAME = "CORDA.ECDSA.SECP256K1"
const val ECDSA_SECP256R1_CODE_NAME = "CORDA.ECDSA.SECP256R1"
const val EDDSA_ED25519_CODE_NAME = "CORDA.EDDSA.ED25519"
const val SM2_CODE_NAME = "CORDA.SM2"
const val GOST3410_GOST3411_CODE_NAME = "CORDA.GOST3410.GOST3411"
const val SPHINCS256_CODE_NAME = "CORDA.SPHINCS-256"
const val COMPOSITE_KEY_CODE_NAME = "COMPOSITE"

// OID taken from https://tools.ietf.org/html/draft-ietf-curdle-pkix-00
@JvmField
val ID_CURVE_25519PH = ASN1ObjectIdentifier("1.3.101.112")

/** DLSequence (ASN1Sequence) for SHA512 truncated to 256 bits, used in SPHINCS-256 signature scheme. */
@JvmField
val SHA512_256 = DLSequence(arrayOf(NISTObjectIdentifiers.id_sha512_256))

@JvmField
val NaSignatureSpec: SignatureSpec = SignatureSpec(
    signatureName = "na",
    signatureOID = null
)

@JvmField
val RSA_SHA256_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA256WITHRSA",
    signatureOID = AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, null)
)

@JvmField
val ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA256withECDSA",
    signatureOID = AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256k1)
)

@JvmField
val ECDSA_SECP256R1_SHA256_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA256withECDSA",
    signatureOID = AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256r1)
)

@JvmField
val EDDSA_ED25519_NONE_SIGNATURE_SPEC = SignatureSpec(
    signatureName = EdDSAEngine.SIGNATURE_ALGORITHM,
    signatureOID = AlgorithmIdentifier(ID_CURVE_25519PH, null)
)

@JvmField
val SPHINCS256_SHA512_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SHA512WITHSPHINCS256",
    signatureOID = AlgorithmIdentifier(BCObjectIdentifiers.sphincs256_with_SHA512, DLSequence(arrayOf(ASN1Integer(0), SHA512_256)))
)

@JvmField
val SM2_SM3_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "SM3withSM2",
    signatureOID = AlgorithmIdentifier(GMObjectIdentifiers.sm2sign_with_sm3, GMObjectIdentifiers.sm2p256v1)
)

@JvmField
val GOST3410_GOST3411_SIGNATURE_SPEC = SignatureSpec(
    signatureName = "GOST3411withGOST3410",
    signatureOID = AlgorithmIdentifier(
        CryptoProObjectIdentifiers.gostR3410_94, DLSequence(
            arrayOf(
                CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A,
                CryptoProObjectIdentifiers.gostR3411_94_CryptoProParamSet
            )
        )
    )
)

@JvmField
val RSA_SHA256_TEMPLATE = SignatureSchemeTemplate(
    codeName = RSA_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, null)),
    algorithmName = "RSA",
    algSpec = null,
    keySize = 3072,
    signatureSpec = RSA_SHA256_SIGNATURE_SPEC
)

@JvmField
val ECDSA_SECP256K1_SHA256_TEMPLATE = SignatureSchemeTemplate(
    codeName = ECDSA_SECP256K1_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
    algorithmName = "EC",
    algSpec = ECNamedCurveTable.getParameterSpec("secp256k1"),
    keySize = null,
    signatureSpec = ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC
)

@JvmField
val ECDSA_SECP256R1_SHA256_TEMPLATE = SignatureSchemeTemplate(
    codeName = ECDSA_SECP256R1_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256r1)),
    algorithmName = "EC",
    algSpec = ECNamedCurveTable.getParameterSpec("secp256r1"),
    keySize = null,
    signatureSpec = ECDSA_SECP256R1_SHA256_SIGNATURE_SPEC
)

@JvmField
val EDDSA_ED25519_NONE_TEMPLATE = SignatureSchemeTemplate(
    codeName = EDDSA_ED25519_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(ID_CURVE_25519PH, null)),
    algorithmName = "1.3.101.112",
    algSpec = EdDSANamedCurveTable.getByName("ED25519"),
    keySize = null,
    signatureSpec = EDDSA_ED25519_NONE_SIGNATURE_SPEC
)

@JvmField
val SPHINCS256_SHA512_TEMPLATE = SignatureSchemeTemplate(
    codeName = SPHINCS256_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(BCObjectIdentifiers.sphincs256, DLSequence(arrayOf(ASN1Integer(0), SHA512_256)))),
    algorithmName = "SPHINCS256",
    algSpec = SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256),
    keySize = null,
    signatureSpec = SPHINCS256_SHA512_SIGNATURE_SPEC
)

@JvmField
val SM2_SM3_TEMPLATE = SignatureSchemeTemplate(
    codeName = SM2_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, GMObjectIdentifiers.sm2p256v1)),
    algorithmName = "EC",
    algSpec = ECNamedCurveTable.getParameterSpec("sm2p256v1"),
    keySize = null,
    signatureSpec = SM2_SM3_SIGNATURE_SPEC
)

@JvmField
val GOST3410_GOST3411_TEMPLATE = SignatureSchemeTemplate(
    codeName = GOST3410_GOST3411_CODE_NAME,
    algorithmOIDs = listOf(
        AlgorithmIdentifier(
            CryptoProObjectIdentifiers.gostR3410_94, DLSequence(
                arrayOf(
                    CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A,
                    CryptoProObjectIdentifiers.gostR3411_94_CryptoProParamSet
                )
            )
        )
    ),
    algorithmName = "GOST3410",
    algSpec = GOST3410ParameterSpec(CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A.id),
    keySize = null,
    signatureSpec = GOST3410_GOST3411_SIGNATURE_SPEC
)

@JvmField
val COMPOSITE_KEY_TEMPLATE = SignatureSchemeTemplate(
    codeName = COMPOSITE_KEY_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(OID_COMPOSITE_KEY_IDENTIFIER)),
    algorithmName = CompositeKey.KEY_ALGORITHM,
    algSpec = null,
    keySize = null,
    signatureSpec = NaSignatureSpec
)
