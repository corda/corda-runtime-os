package net.corda.crypto.service.impl.signing

import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.service.CryptoServiceRef
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningAliasSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.exceptions.CryptoServiceException

fun CryptoServiceRef.getSupportedSchemes(): List<String> =
    instance.supportedSchemes().map { it.codeName }

fun CryptoServiceRef.generateKeyPair(
    alias: String?,
    scheme: SignatureScheme,
    context: Map<String, String>
): GeneratedKey =
    instance.generateKeyPair(
        KeyGenerationSpec(
            tenantId = tenantId,
            category = category,
            signatureScheme = scheme,
            alias = alias,
            masterKeyAlias = masterKeyAlias,
            secret = aliasSecret
        ),
        context
    )

fun CryptoServiceRef.toSaveKeyContext(
    key: GeneratedKey,
    alias: String?,
    scheme: SignatureScheme,
    externalId: String?
): SigningKeySaveContext =
    when(key) {
        is GeneratedPublicKey -> SigningPublicKeySaveContext(
            key = key,
            alias = alias,
            signatureScheme = scheme,
            category = category,
            externalId = externalId,
        )
        is GeneratedWrappedKey -> SigningWrappedKeySaveContext(
            key = key,
            masterKeyAlias = masterKeyAlias,
            externalId = externalId,
            alias = alias,
            signatureScheme = scheme,
            category = category
        )
        else -> throw CryptoServiceException("Unknown key generation response: ${key::class.java.name}")
    }

fun CryptoServiceRef.sign(
    record: SigningCachedKey,
    scheme: SignatureScheme,
    data: ByteArray,
    context: Map<String, String>
): ByteArray {
    val spec = if(record.keyMaterial != null) {
        require(record.keyMaterial!!.isNotEmpty()) {
            "The key material is empty."
        }
        require(record.encodingVersion != null) {
            "The encoding version is missing."
        }
        SigningWrappedSpec(
            tenantId = tenantId,
            keyMaterial = record.keyMaterial!!,
            masterKeyAlias = record.masterKeyAlias,
            encodingVersion = record.encodingVersion!!,
            signatureScheme = scheme
        )
    } else {
        require(!record.hsmAlias.isNullOrBlank()) {
            "The hsm assigned alias is missing."
        }
        SigningAliasSpec(
            tenantId = tenantId,
            hsmAlias = record.hsmAlias!!,
            signatureScheme = scheme
        )
    }
    return instance.sign(spec, data, context)
}
