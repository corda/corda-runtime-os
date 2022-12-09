package net.corda.crypto.service.impl

import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.v5.base.util.uncheckedCast

/**
 * Handles crypto requests and returns response. Because requests are coming in as (Avro) [Object]s we grab appropriate handler
 * by actual request's [Class].
 */
interface CryptoRequestHandler<REQUEST, RESPONSE> {
    val requestClass: Class<REQUEST>
    fun handle(request: REQUEST, context: CryptoRequestContext): RESPONSE
}

fun Map<Class<*>, CryptoRequestHandler<*, out Any>>.getHandlerForRequest(requestType: Class<*>): CryptoRequestHandler<Any, out Any> =
    uncheckedCast(
        this[requestType]
            ?: throw IllegalArgumentException("Unknown request type ${requestType.name}")
    )
