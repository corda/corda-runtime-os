package net.corda.utxo.token.sync.factories

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.messaging.api.records.Record
import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.TokenRecord
import net.corda.utxo.token.sync.entities.TokenRefRecord
import net.corda.virtualnode.HoldingIdentity

interface MessagingRecordFactory {
    fun createTokenPoolCacheEvent(
        holdingIdentity: HoldingIdentity,
        key: TokenPoolKeyRecord,
        unspentTokens: List<TokenRecord>,
        spentTokens: List<TokenRecord>
    ): Record<TokenPoolCacheKey, TokenPoolCacheEvent>

    fun createSyncCheckEvent(
        holdingIdentity: HoldingIdentity,
        key: TokenPoolKeyRecord,
        unspentTokens: List<TokenRefRecord>
    ): Record<TokenPoolCacheKey, TokenPoolCacheEvent>

    fun createSyncWakeup(
        key:HoldingIdentity
    ): Record<String, TokenSyncEvent>
}
