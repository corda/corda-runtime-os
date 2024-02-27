package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.TokenEvent

interface TokenSelectionMetrics {
    fun <T> recordProcessingTime(tokenEvent: TokenEvent, block: () -> T): T

    fun <T> recordDbOperationTime(dbOperation: String, block: () -> T): T

    fun <T> entityManagerCreationTime(block: () -> T): T
}
