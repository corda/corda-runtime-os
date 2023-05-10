package com.r3.corda.demo.utxo.token

data class CreateCoinMessage(
    val issuerBankX500: String,
    val currency: String,
    val numberOfCoins: Int,
    val valueOfCoin: Int,
    val tag: String?,
    val ownerHash: String?
)
