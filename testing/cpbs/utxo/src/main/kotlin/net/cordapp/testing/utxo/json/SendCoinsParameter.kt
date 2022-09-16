package net.cordapp.testing.utxo.json

data class SendCoinsParameter(
    val recipientX500Name: String? = null,
    val amount: Long? = null
)
