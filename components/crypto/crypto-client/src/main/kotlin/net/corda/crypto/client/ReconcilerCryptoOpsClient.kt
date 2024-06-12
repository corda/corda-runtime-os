package net.corda.crypto.client

import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.lifecycle.Lifecycle

interface ReconcilerCryptoOpsClient: Lifecycle {
    fun lookupKeysByIds(tenantId: String, keyIds: List<ShortHash>): List<CryptoSigningKey>
}