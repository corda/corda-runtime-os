package net.corda.utxo.token.sync.factories.impl

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenCachedSyncCheck
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.data.TokenSyncWakeUp
import net.corda.data.ledger.utxo.token.selection.data.TokenUnspentSyncCheck
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Services.Companion.TOKEN_CACHE_EVENT
import net.corda.schema.Schemas.Services.Companion.TOKEN_CACHE_SYNC_EVENT
import net.corda.utxo.token.sync.converters.EntityConverter
import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.TokenRecord
import net.corda.utxo.token.sync.entities.TokenRefRecord
import net.corda.utxo.token.sync.factories.MessagingRecordFactory
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import java.nio.ByteBuffer

class MessagingRecordFactoryImpl(
    private val entityConverter: EntityConverter
) : MessagingRecordFactory {

    override fun createTokenPoolCacheEvent(
        holdingIdentity: HoldingIdentity,
        key: TokenPoolKeyRecord,
        unspentTokens: List<TokenRecord>,
        spentTokens: List<TokenRecord>
    ): Record<TokenPoolCacheKey, TokenPoolCacheEvent> {
        val avroPoolKey = entityConverter.toTokenPoolKey(holdingIdentity, key)

        val ledgerChangeMessage = TokenLedgerChange().apply {
            this.poolKey = avroPoolKey
            this.producedTokens = unspentTokens.map { createAvroToken(it) }
            this.consumedTokens = spentTokens.map { createAvroToken(it) }
        }

        return createCacheEventRecord(avroPoolKey, ledgerChangeMessage)
    }

    override fun createSyncCheckEvent(
        holdingIdentity: HoldingIdentity,
        key: TokenPoolKeyRecord,
        unspentTokens: List<TokenRefRecord>
    ): Record<TokenPoolCacheKey, TokenPoolCacheEvent> {
        val avroPoolKey = entityConverter.toTokenPoolKey(holdingIdentity, key)

        val unspentSyncCheck = TokenCachedSyncCheck().apply {
            this.poolKey = avroPoolKey
            this.tokenRefs = unspentTokens.map { it.stateRef }
        }

        return createCacheEventRecord(avroPoolKey, unspentSyncCheck)
    }

    override fun createSyncWakeup(key: HoldingIdentity): Record<String, TokenSyncEvent> {
        val eventMessage = TokenSyncEvent().apply {
            this.holdingIdentity = key.toAvro()
            this.payload = TokenSyncWakeUp()
        }

        return Record(TOKEN_CACHE_SYNC_EVENT, key.shortHash.toString(), eventMessage)
    }

    private fun createCacheEventRecord(
        key: TokenPoolCacheKey,
        payload: Any
    ): Record<TokenPoolCacheKey, TokenPoolCacheEvent> {
        val eventMessage = TokenPoolCacheEvent().apply {
            this.poolKey = key
            this.payload = payload
        }

        return Record(TOKEN_CACHE_EVENT, key, eventMessage)
    }

    private fun createAvroToken(record: TokenRecord): Token {
        val tokenAmount = TokenAmount().apply {
            this.unscaledValue = ByteBuffer.wrap(record.amount.unscaledValue().toByteArray())
            this.scale = record.amount.scale()
        }
        return Token().apply {
            this.amount = tokenAmount
            this.tag = record.tag
            this.ownerHash = record.ownerHash
            this.stateRef = record.stateRef
        }
    }
}
