package net.corda.messaging.api.subscription

import net.corda.messaging.api.processor.HttpRPCProcessor

interface HttpRPCSubscription {
    fun <REQ : Any, RESP : Any> registerEndpoint(endpoint: String, handler: HttpRPCProcessor<REQ, RESP>, clazz: Class<REQ>)
}