package net.corda.messaging.api.subscription

interface HttpRPCSubscription {
    fun <REQ: Any, RESP: Any> registerEndpoint(endpoint: String, handler: (REQ) -> RESP, clazz: Class<REQ>)
}