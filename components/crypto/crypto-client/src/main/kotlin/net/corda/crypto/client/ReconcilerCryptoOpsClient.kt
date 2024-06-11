package net.corda.crypto.client

import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey

interface ReconcilerCryptoOpsClient {
    fun lookupKeysByIds(tenantId: String, keyIds: List<ShortHash>): List<CryptoSigningKey>
}