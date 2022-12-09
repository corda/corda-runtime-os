package net.corda.crypto.service.impl

import net.corda.data.crypto.wire.CryptoRequestContext
import java.lang.reflect.Constructor
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles crypto requests and returns response. Because requests are coming in as (Avro) [Object]s we grab appropriate handler
 * by actual object's [Class].
 */
interface CryptoRequestHandler<REQUEST, RESPONSE> {
    val requestClass: Class<REQUEST>
    fun handle(request: REQUEST, context: CryptoRequestContext): RESPONSE
}

@Suppress("UNCHECKED_CAST")
fun Map<Class<*>, CryptoRequestHandler<*, out Any>>.getHandlerForRequest(requestType: Class<*>): CryptoRequestHandler<Any, out Any> =
    this[requestType] as? CryptoRequestHandler<Any, out Any>
        ?: throw IllegalArgumentException("Unknown request type ${requestType.name}")
