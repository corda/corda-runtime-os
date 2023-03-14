package net.cordapp.testing.packagingverification

data class TransferRequest(
    val value: Long,
    val recipientX500Name: String
)
