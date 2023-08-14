package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenBalance
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey

interface AvailableTokenService {

    fun findAvailTokens(poolKey: TokenPoolKey, ownerHash: String?, tagRegex: String?): AvailTokenQueryResult

    fun queryBalance(poolKey: TokenPoolKey, ownerHash: String?, tagRegex: String?, claimedTokens: Collection<CachedToken>): TokenBalance

}