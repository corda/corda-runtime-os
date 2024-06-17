package net.corda.crypto.client

import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.lifecycle.Lifecycle

interface ReconcilerCryptoOpsClient: Lifecycle {
    /**
     * Returns keys with key ids in [keyIds] which are owned by tenant of id [tenantId].
     *
     * Please note that this method uses short key ids which means that a key clash on short key id is more likely to happen.
     * Effectively it means that a requested key not owned by the tenant, however having same short key id (clashing short key id)
     * with a different key which is owned by the tenant, will lead to return the owned key while it should not.
     * This api is added for convenience of using short hashes in cases like for e.g. REST interfaces. For more critical
     * key operations where we need to avoid key id clashes [lookupKeysByFullIds] can be used.
     */
    fun lookupKeysByIds(tenantId: String, keyIds: List<ShortHash>): List<CryptoSigningKey>
}