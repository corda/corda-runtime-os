package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.SignatureScheme

/**
 * Defines parameters to generate a key pair.
 *
 * @property tenantId The tenant id which the key pair is generated for.
 * @property signatureScheme The spec defining properties of the key pair being generated.
 * @property alias Optional, the key alias for the pair as defined by the tenant, as that value is not guarantied to be
 * unique, in case if the HSM is shared between several tenants, the implementation must translate it something unique,
 * as an example it can use the provided utility function [computeHSMAlias] which uses the tenantId and alias to calculate
 * HMAC as the unique alias. The null value indicates that the generated key should be wrapped. The actual implementation
 * may always generate keys persisted in HSM (if that natively supports large number of the keys), so if the alias is null
 * then the implementation will have to generate some unique value by itself, or always generate wrapped keys.
 * @property masterKeyAlias The wrapping key alias which can be used when generating the wrapped keys, the value
 * could be null for HSMs which use built-in wrapping keys.
 * @property secret The configured secret that can be used to generate the unique HSM alias.
 *
 * Note about key aliases. Corda always uses single alias to identify a key pair however some HSMs need separate
 * aliases for public and private keys, in such cases their names have to be derived from the single key pair alias.
 * It could be suffixes or whatever internal naming scheme is used.
 */
class KeyGenerationSpec(
    val tenantId: String,
    val signatureScheme: SignatureScheme,
    val alias: String?,
    val masterKeyAlias: String?,
    val secret: ByteArray?
)

