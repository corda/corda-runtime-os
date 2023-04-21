package net.corda.ledger.utxo.token.cache.converters

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimRelease
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.ClaimRelease
import net.corda.ledger.utxo.token.cache.entities.LedgerChange
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import java.math.BigDecimal

/**
 * The [EntityConverter] converts Avro entities to model entities
 */
interface EntityConverter {

    /**
     * Creates a [CachedToken] from an Avro [Token]
     *
     * @param avroToken The Avro representation of a token
     *
     * @return An instance of [CachedToken]
     */
    fun toCachedToken(avroToken: Token): CachedToken

    /**
     * Creates a [TokenCache] from an Avro [TokenPoolCacheState]
     *
     * @param avroCacheState The Avro representation of a cached state
     *
     * @return An instance of [TokenCache]
     */
    fun toTokenCache(avroCacheState: TokenPoolCacheState): TokenCache

    /**
     * Creates a [PoolCacheState] from an Avro [TokenPoolCacheState]
     *
     * @param avroCacheState The Avro representation of a cached state
     *
     * @return An instance of [PoolCacheState]
     */
    fun toPoolCacheState(avroCacheState: TokenPoolCacheState): PoolCacheState

    /**
     * Creates a [ClaimQuery] from an Avro [TokenClaimQuery]
     *
     * @param avroPoolKey The pool key the claim query is for
     * @param tokenClaimQuery The Avro representation of a claim query
     *
     * @return An instance of [ClaimQuery]
     */
    fun toClaimQuery(avroPoolKey: TokenPoolCacheKey, tokenClaimQuery: TokenClaimQuery): ClaimQuery

    /**
     * Creates a [ClaimRelease] from an Avro [TokenClaimRelease]
     *
     * @param avroPoolKey The pool key the claim release is for
     * @param tokenClaimRelease The Avro representation of a claim release
     *
     * @return An instance of [ClaimRelease]
     */
    fun toClaimRelease(avroPoolKey: TokenPoolCacheKey, tokenClaimRelease: TokenClaimRelease): ClaimRelease

    /**
     * Creates a [LedgerChange] from an Avro [TokenLedgerChange]
     *
     * @param avroPoolKey The pool key the change event is for
     * @param tokenLedgerChange The Avro representation of change event
     *
     * @return An instance of [LedgerChange]
     */
    fun toLedgerChange(avroPoolKey: TokenPoolCacheKey, tokenLedgerChange: TokenLedgerChange): LedgerChange

    /**
     * Creates a [BigDecimal] from an Avro [TokenAmount]
     *
     * @param avroTokenAmount The Avro representation of token amount
     *
     * @return An instance of [BigDecimal]
     */
    fun amountToBigDecimal(avroTokenAmount: TokenAmount): BigDecimal
}

