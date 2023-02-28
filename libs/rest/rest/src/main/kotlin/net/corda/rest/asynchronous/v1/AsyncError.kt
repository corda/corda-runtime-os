package net.corda.rest.asynchronous.v1

data class AsyncError(
    val message: String,
    val details: Map<String, String>?
)