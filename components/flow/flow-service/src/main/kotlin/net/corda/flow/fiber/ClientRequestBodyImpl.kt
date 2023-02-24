package net.corda.flow.fiber

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.marshalling.MarshallingService

/**
 * Read the start args from the start context. This prevents the full start args from being serialized on every
 * checkpoint for a Rest started flow.
 */
class ClientRequestBodyImpl(private val fiberService: FlowFiberService) : ClientRequestBody {

    companion object {
        private const val MAX_STRING_LENGTH = 200
    }

    override fun getRequestBody(): String {
        return fiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.flowStartContext.startArgs
            ?: throw IllegalStateException("Failed to find the start args for Rest started flow")
    }

    override fun <T : Any> getRequestBodyAs(marshallingService: MarshallingService, clazz: Class<T>): T {
        return marshallingService.parse(requestBody, clazz)
    }

    override fun <T> getRequestBodyAsList(marshallingService: MarshallingService, clazz: Class<T>): List<T> {
        return marshallingService.parseList(requestBody, clazz)
    }

    override fun <K, V> getRequestBodyAsMap(marshallingService: MarshallingService,
                                            keyClass: Class<K>,
                                            valueClass: Class<V>): Map<K, V> {
        return marshallingService.parseMap(requestBody, keyClass, valueClass)
    }

    override fun toString(): String {
        // Truncate the JSON object to ensure that we don't try and write too much data into logs.
        return "ClientRequestBody(input=${requestBody.take(MAX_STRING_LENGTH)})"
    }
}