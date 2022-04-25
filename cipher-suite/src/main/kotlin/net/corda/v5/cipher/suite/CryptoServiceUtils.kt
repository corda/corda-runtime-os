@file:JvmName("CryptoServiceUtils")

package net.corda.v5.cipher.suite

import net.corda.v5.base.types.toHexString
import net.corda.v5.crypto.HMAC_SHA256_ALGORITHM
import net.corda.v5.crypto.hmac

/**
 * Computes an alias based on the value supplied by the tenant. As the HSM can be shared across several tenants
 * that will provide a level of separation.
 *
 * The default implementation computes HMAC (HmacSHA256) for concatenation of tenant's id and their alias
 * then transforms it to HEX and takes first 12 characters of that result.
 *
 * @param tenantId The tenant's id which the [alias] belongs to
 * @param alias Alias as supplied by the [tenantId].
 * @param secret Secret.
 *
 * @return computed alias which must be unique and must be deterministic, e.g. for the same
 * inputs ([tenantId] and [alias]) always produce the same output.
 *
 * @throws IllegalArgumentException if the tenant, alias or secret are empty.
 */
fun computeHSMAlias(
    tenantId: String,
    alias: String,
    secret: ByteArray
): String {
    require(tenantId.isNotBlank()) {
        "The tenant id cannot be empty."
    }
    require(alias.isNotBlank()) {
        "The alias cannot be empty or null."
    }
    require(secret.isNotEmpty()) {
        "The secret cannot be empty."
    }
    return (tenantId + alias)
        .encodeToByteArray()
        .hmac(secret, HMAC_SHA256_ALGORITHM)
        .toHexString()
        .take(12)
}