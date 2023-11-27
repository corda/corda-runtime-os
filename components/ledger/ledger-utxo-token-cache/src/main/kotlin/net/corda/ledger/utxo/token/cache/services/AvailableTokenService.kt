package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.v5.ledger.utxo.token.selection.TokenBalance

interface AvailableTokenService {

    fun findAvailTokens(poolKey: TokenPoolKey, ownerHash: String?, tagRegex: String?, maxTokens: Int): AvailTokenQueryResult

    fun queryBalance(poolKey: TokenPoolKey, ownerHash: String?, tagRegex: String?, claimedTokens: Collection<CachedToken>): TokenBalance

}