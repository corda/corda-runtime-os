package net.corda.crypto.cipher.suite

import net.corda.crypto.cipher.suite.schemes.KeyScheme

/**
 * Parameters to generate a key pair.
 *
 * @property keyScheme The spec defining properties of the key pair being generated.
 * @property alias An optional property, this is the key alias for the pair as defined by the tenant.
 * @property wrappingKeyAlias the alias to use for the wrapping key when storing at rest in the database.
 *
 * As that value is not guarantied to be unique when the HSM is shared between several tenants,
 * the implementation must translate it to something unique. As an example, it can use the provided
 * utility function computeHSMAlias which uses the tenantId and alias to calculate HMAC as the unique alias.
 * The null value indicates that the generated key should be wrapped. The actual implementation may always
 * generate keys persisted in HSM (if that natively supports a large number of the keys) so, if the alias is null,
 * the implementation will have to generate some unique value by itself, or always generate wrapped keys.
 *
 * The Corda Platform always uses single alias to identify a key pair however some HSMs need separate
 * aliases for public and private keys, in such cases their names have to be derived from the single key pair alias.
 * It could be suffixes or whatever internal naming scheme is used.
 */
class KeyGenerationSpec(
    val keyScheme: KeyScheme,
    val alias: String?,
    val wrappingKeyAlias: String
) {
    override fun toString(): String {
        return "$keyScheme,alias=$alias,masterKeyAlias=$wrappingKeyAlias"
    }
}

