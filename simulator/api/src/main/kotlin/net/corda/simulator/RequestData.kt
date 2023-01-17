package net.corda.simulator

import net.corda.simulator.exceptions.ServiceConfigurationException
import net.corda.simulator.factories.RequestDataFactory
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCRequestData
import java.util.ServiceLoader

/**
 * A wrapper around [net.corda.v5.application.flows.RPCRequestData] which can be passed to Simulator
 * on initiation of flows. This interface can be implemented, but static / companion factory methods
 * are provided for convenience.
 */
interface RequestData {

    /**
     * The client request which would be used by Corda to identify a particular request for a flow call.
     */
    val clientRequestId: String

    /**
     * The name of the flow class to be run.
     */
    val flowClassName: String

    /**
     * A JSON string containing the request body to be passed to the flow.
     */
    val requestData: String
    fun toRPCRequestData(): RPCRequestData

    companion object {
        private val factory = ServiceLoader.load(RequestDataFactory::class.java).firstOrNull() ?:
            throw ServiceConfigurationException(RequestDataFactory::class.java)

        /**
         * Creates a [RequestData] using the given strongly-typed parameters.
         *
         * @param requestId The client request which would be used by Corda to identify a particular request
         * for a flow call.
         * @param flowClass The flow class to be constructed and called.
         * @param request Data which will be serialized using a
         * [net.corda.v5.application.marshalling.JsonMarshallingService] and passed to the flow.
         * @return A [RequestData] with properties that match the provided parameters.
         */
        @JvmStatic
        fun create(requestId: String, flowClass: Class<out Flow>, request: Any): RequestData
            = factory.create(requestId, flowClass, request)

        /**
         * Creates a [RequestData] using the given parameters. The strings used in this method are the same that
         * would be passed to Swagger UI if using it.
         *
         * @param requestId The client request which would be used by Corda to identify a particular request
         * for a flow call.
         * @param flowClass The name of the flow class to be constructed and called.
         * @param request Data to be passed to the flow.
         * @return A [RequestData] with the provided parameters as properties.
         */
        @JvmStatic
        fun create(requestId: String, flowClass: String, request: String) : RequestData
            = factory.create(requestId, flowClass, request)

        /**
         * Creates a [RequestData] using the provided input. The input used in this method is the same that would
         * be provided to `curl` if using it.
         *
         * @param jsonInput A JSON-formatted string containing a client-provided `requestId`, the `flowClass` to
         * be constructed and called and the `requestBody` to be passed into the flow.
         */
        @JvmStatic
        fun create(jsonInput : String) = factory.create(jsonInput)

        /**
         * Creates a valid [RequestData] object that can be used for flows which ignore it. This can be useful for
         * instance flows where the behaviour can be explicitly specified regardless of the input.
         */
        @JvmStatic
        val IGNORED = create("r1", Flow::class.java, "")
    }
}