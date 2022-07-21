package net.corda.crypto.service

import net.corda.crypto.persistence.signing.SigningKeySaveContext
import net.corda.crypto.persistence.signing.SigningPublicKeySaveContext
import net.corda.crypto.persistence.signing.SigningWrappedKeySaveContext
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.schemes.KeyScheme

/**
 * Defines a reference to an instance of [CryptoService] with configuration information per tenant.
 */
@Suppress("LongParameterList")
class CryptoServiceRef(
    val tenantId: String,
    val category: String,
    val masterKeyAlias: String?,
    val aliasSecret: ByteArray?,
    val workerSetId: String,
    val instance: CryptoService
) {
    fun toSaveKeyContext(
        key: GeneratedKey,
        alias: String?,
        scheme: KeyScheme,
        externalId: String?
    ): SigningKeySaveContext =
        when (key) {
            is GeneratedPublicKey -> SigningPublicKeySaveContext(
                key = key,
                alias = alias,
                keyScheme = scheme,
                category = category,
                workerSetId = workerSetId,
                externalId = externalId
            )
            is GeneratedWrappedKey -> SigningWrappedKeySaveContext(
                key = key,
                masterKeyAlias = masterKeyAlias,
                externalId = externalId,
                alias = alias,
                keyScheme = scheme,
                category = category,
                workerSetId = workerSetId
            )
            else -> throw IllegalStateException("Unknown key generation response: ${key::class.java.name}")
        }
}