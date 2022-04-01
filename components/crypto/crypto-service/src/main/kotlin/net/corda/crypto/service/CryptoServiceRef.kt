package net.corda.crypto.service

import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.schemes.SignatureScheme

/**
 * Defines a reference to an instance of [CryptoService] with configuration information per tenant.
 */
class CryptoServiceRef(
    val tenantId: String,
    val category: String,
    val signatureScheme: SignatureScheme,
    val masterKeyAlias: String?,
    val aliasSecret: ByteArray?,
    val instance: CryptoService
)