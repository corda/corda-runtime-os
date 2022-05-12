package net.corda.crypto.service

import net.corda.v5.cipher.suite.CryptoService

/**
 * Defines a reference to an instance of [CryptoService] with configuration information per tenant.
 */
@Suppress("LongParameterList")
class CryptoServiceRef(
    val tenantId: String,
    val category: String,
    val masterKeyAlias: String?,
    val aliasSecret: ByteArray?,
    val instance: CryptoService
)