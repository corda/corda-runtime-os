package net.corda.crypto.service

import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.GeneratedKey
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext

/**
 * Defines a reference to an instance of [CryptoService] with configuration information per tenant.
 */
@Suppress("LongParameterList")
class CryptoServiceRef(
    val tenantId: String,
    val category: String,
    val masterKeyAlias: String?,
    val hsmId: String,
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
                hsmId = hsmId,
                externalId = externalId
            )
            is GeneratedWrappedKey -> SigningWrappedKeySaveContext(
                key = key,
                masterKeyAlias = masterKeyAlias,
                externalId = externalId,
                alias = alias,
                keyScheme = scheme,
                category = category,
                hsmId = hsmId
            )
            else -> throw IllegalStateException("Unknown key generation response: ${key::class.java.name}")
        }
}