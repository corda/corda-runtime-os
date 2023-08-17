package net.corda.ledger.utxo.token.cache.entities

data class TokenPoolKey(
    val shortHolderId: String,
    val tokenType: String,
    val issuerHash: String,
    val notaryX500Name: String,
    val symbol: String
)
