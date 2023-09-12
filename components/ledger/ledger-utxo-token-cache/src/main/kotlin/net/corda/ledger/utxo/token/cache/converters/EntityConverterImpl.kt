package net.corda.ledger.utxo.token.cache.converters

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimRelease
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.entities.BalanceQuery
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.internal.CachedTokenImpl
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.ClaimRelease
import net.corda.ledger.utxo.token.cache.entities.LedgerChange
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.internal.PoolCacheStateImpl
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import java.math.BigDecimal
import java.math.BigInteger

class EntityConverterImpl : EntityConverter {
    override fun toCachedToken(avroToken: Token): CachedToken {
        return CachedTokenImpl(avroToken, this)
    }

    override fun toPoolCacheState(avroCacheState: TokenPoolCacheState): PoolCacheState {
        return PoolCacheStateImpl(avroCacheState)
    }

    override fun toClaimQuery(avroPoolKey: TokenPoolCacheKey, tokenClaimQuery: TokenClaimQuery): ClaimQuery {
        return ClaimQuery(
            tokenClaimQuery.requestContext.requestId,
            tokenClaimQuery.requestContext.flowId,
            amountToBigDecimal(tokenClaimQuery.targetAmount),
            tokenClaimQuery.tagRegex,
            tokenClaimQuery.ownerHash,
            toTokenPoolKey(avroPoolKey)
        )
    }

    override fun toClaimRelease(avroPoolKey: TokenPoolCacheKey, tokenClaimRelease: TokenClaimRelease): ClaimRelease {
        return ClaimRelease(
            tokenClaimRelease.claimId,
            tokenClaimRelease.requestContext.requestId,
            tokenClaimRelease.requestContext.flowId,
            tokenClaimRelease.usedTokenStateRefs.toSet(),
            toTokenPoolKey(avroPoolKey)
        )
    }

    override fun toBalanceQuery(avroPoolKey: TokenPoolCacheKey, tokenBalanceQuery: TokenBalanceQuery): BalanceQuery {
        return BalanceQuery(
            tokenBalanceQuery.requestContext.requestId,
            tokenBalanceQuery.requestContext.flowId,
            tokenBalanceQuery.tagRegex,
            tokenBalanceQuery.ownerHash,
            toTokenPoolKey(avroPoolKey)
        )
    }

    override fun toLedgerChange(avroPoolKey: TokenPoolCacheKey, tokenLedgerChange: TokenLedgerChange): LedgerChange {
        return LedgerChange(
            toTokenPoolKey(avroPoolKey),
            // HACK: Added for testing will be removed by CORE-5722 (ledger integration)
            null,
            null,
            tokenLedgerChange.consumedTokens.map { toCachedToken(it) },
            tokenLedgerChange.producedTokens.map { toCachedToken(it) }
        )
    }

    override fun amountToBigDecimal(avroTokenAmount: TokenAmount): BigDecimal {
        val unscaledValueBytes = ByteArray(avroTokenAmount.unscaledValue.remaining())
            .apply { avroTokenAmount.unscaledValue.get(this) }

        avroTokenAmount.unscaledValue.position(0)
        return BigDecimal(
            BigInteger(unscaledValueBytes),
            avroTokenAmount.scale
        )
    }

    override fun toTokenPoolKey(avroTokenPoolKey: TokenPoolCacheKey): TokenPoolKey {
        return TokenPoolKey(
            avroTokenPoolKey.shortHolderId,
            avroTokenPoolKey.tokenType,
            avroTokenPoolKey.issuerHash,
            avroTokenPoolKey.notaryX500Name,
            avroTokenPoolKey.symbol)
    }
}
