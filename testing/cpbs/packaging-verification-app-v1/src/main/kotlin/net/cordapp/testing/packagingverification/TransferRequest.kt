package net.cordapp.testing.packagingverification

data class TransferRequest(
    val value: Long,
    val issuerX500Name: String,
    val recipientX500Name: String
)
