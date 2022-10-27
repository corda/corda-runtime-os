package net.corda.httprpc.response

data class AsyncOperationError(
    val code: String,
    val message: String,
    val details: Map<String, String>?
)