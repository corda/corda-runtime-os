package net.corda.crypto.service

import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.persistence.SigningWrappedKeySaveContext

/**
 * Defines a reference to an instance of [CryptoService] with configuration information per tenant.
 */
@Suppress("LongParameterList")
class CryptoServiceRef(
    val tenantId: String,
    val category: String,
    val masterKeyAlias: String,
    val hsmId: String,
    val instance: CryptoService
) {
    fun toSaveKeyContext(
        key: GeneratedWrappedKey,
        alias: String?,
        scheme: KeyScheme,
        externalId: String?
    ): SigningWrappedKeySaveContext = SigningWrappedKeySaveContext(
        wrappingKeyAlias = masterKeyAlias,
        key = key,
        externalId = externalId,
        alias = alias,
        keyScheme = scheme,
        category = category
    )
}