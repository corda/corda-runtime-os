package net.corda.crypto.service.impl

import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import java.lang.reflect.Constructor
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for request processors
 *
 * @property handlers map of request type to the handler type which handles that request.
 */
abstract class WireProcessor(
    private val handlers: Map<Class<*>, Class<out Handler<out Any>>>
) {
    /**
     * Interface which must e implemnted by request handlers.
     */
    interface Handler<REQUEST> {
        fun handle(context: CryptoRequestContext, request: REQUEST): Any
    }

    private val constructors = ConcurrentHashMap<Class<*>, Constructor<*>>()

    /**
     * Gets the request handler class, the class must have only single constructor which acceopts single
     * parameter passed here as handlerCtorArg
     */
    @Suppress("UNCHECKED_CAST")
    protected fun getHandler(request: Class<*>, handlerCtorArg: Any): Handler<Any> {
        val constructor = constructors.computeIfAbsent(request) {
            val type = handlers[request] ?: throw CryptoServiceBadRequestException(
                "Unknown request type ${request.name}"
            )
            type.constructors.first {
                it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(handlerCtorArg::class.java)
            }.apply { isAccessible = true }
        }
        return constructor.newInstance(handlerCtorArg) as Handler<Any>
    }
}