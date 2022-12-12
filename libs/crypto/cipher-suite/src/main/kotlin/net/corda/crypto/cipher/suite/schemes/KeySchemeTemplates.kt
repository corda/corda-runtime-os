@file:JvmName("KeySchemeTemplates")

package net.corda.crypto.cipher.suite.schemes

import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.X25519_CODE_NAME
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
import org.bouncycastle.jcajce.spec.EdDSAParameterSpec
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.GOST3410ParameterSpec
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec


/**
 * OID of the EdDSA 25519PH Curve.
 * The OID taken from https://tools.ietf.org/html/draft-ietf-curdle-pkix-00
 */
@JvmField
val ID_CURVE_25519PH = ASN1ObjectIdentifier("1.3.101.112")

/**
 * OID of the X25519 Curve.
 * The OID taken from https://datatracker.ietf.org/doc/html/rfc8410#section-3
 */
@JvmField
val ID_CURVE_X25519 = ASN1ObjectIdentifier("1.3.101.110")

/**
 * DLSequence (ASN1Sequence) for SHA512 truncated to 256 bits, used in SPHINCS-256 key scheme.
 */
@JvmField
val SHA512_256 = DLSequence(arrayOf(NISTObjectIdentifiers.id_sha512_256))

/**
 * Template to create [KeyScheme] for RSA.
 */
@JvmField
val RSA_TEMPLATE = KeySchemeTemplate(
    codeName = RSA_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, null)),
    algorithmName = "RSA",
    algSpec = null,
    keySize = 3072,
    capabilities = setOf(KeySchemeCapability.SIGN)
)

/**
 * Template to create [KeyScheme] for ECDSA with SECP256K1 curve.
 */
@JvmField
val ECDSA_SECP256K1_TEMPLATE = KeySchemeTemplate(
    codeName = ECDSA_SECP256K1_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
    algorithmName = "EC",
    algSpec = ECNamedCurveTable.getParameterSpec("secp256k1"),
    keySize = null,
    capabilities = setOf(KeySchemeCapability.SIGN, KeySchemeCapability.SHARED_SECRET_DERIVATION)
)

/**
 * Template to create [KeyScheme] for ECDSA with SECP256R1 curve.
 */
@JvmField
val ECDSA_SECP256R1_TEMPLATE = KeySchemeTemplate(
    codeName = ECDSA_SECP256R1_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256r1)),
    algorithmName = "EC",
    algSpec = ECNamedCurveTable.getParameterSpec("secp256r1"),
    keySize = null,
    capabilities = setOf(KeySchemeCapability.SIGN, KeySchemeCapability.SHARED_SECRET_DERIVATION)
)

/**
 * Template to create [KeyScheme] for EdDSA with 25519PH curve.
 */
@JvmField
val EDDSA_ED25519_TEMPLATE = KeySchemeTemplate(
    codeName = EDDSA_ED25519_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(ID_CURVE_25519PH, null)),
    algorithmName = "Ed25519",
    algSpec = EdDSAParameterSpec(EdDSAParameterSpec.Ed25519),
    keySize = null,
    capabilities = setOf(KeySchemeCapability.SIGN)
)

/**
 * Template to create [KeyScheme] for EdDSA with X25519 curve for ECDH.
 *
 */
@JvmField
val X25519_TEMPLATE = KeySchemeTemplate(
    codeName = X25519_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(ID_CURVE_X25519, null)),
    algorithmName = "X25519",
    algSpec = null,
    keySize = null,
    capabilities = setOf(KeySchemeCapability.SHARED_SECRET_DERIVATION)
)

/**
 * Template to create [KeyScheme] for SPHINCS.
 */
@JvmField
val SPHINCS256_TEMPLATE = KeySchemeTemplate(
    codeName = SPHINCS256_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(BCObjectIdentifiers.sphincs256, DLSequence(arrayOf(ASN1Integer(0), SHA512_256)))),
    algorithmName = "SPHINCS256",
    algSpec = SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256),
    keySize = null,
    capabilities = setOf(KeySchemeCapability.SIGN)
)

/**
 * Template to create [KeyScheme] for SM2.
 */
@JvmField
val SM2_TEMPLATE = KeySchemeTemplate(
    codeName = SM2_CODE_NAME,
    algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, GMObjectIdentifiers.sm2p256v1)),
    algorithmName = "EC",
    algSpec = ECNamedCurveTable.getParameterSpec("sm2p256v1"),
    keySize = null,
    capabilities = setOf(KeySchemeCapability.SIGN, KeySchemeCapability.SHARED_SECRET_DERIVATION)
)

/**
 * Template to create [KeyScheme] for GOST3410 with GOST3411.
 */
@JvmField
val GOST3410_GOST3411_TEMPLATE = KeySchemeTemplate(
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
    capabilities = setOf(KeySchemeCapability.SIGN)
)

@JvmField
val all = listOf(
    RSA_TEMPLATE,
    EDDSA_ED25519_TEMPLATE,
    X25519_TEMPLATE,
    ECDSA_SECP256K1_TEMPLATE,
    ECDSA_SECP256R1_TEMPLATE,
    SM2_TEMPLATE,
    GOST3410_GOST3411_TEMPLATE,
    SPHINCS256_TEMPLATE
)