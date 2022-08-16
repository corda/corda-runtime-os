package net.corda.testutils.tools

import net.corda.testutils.internal.HttpStartFlow
import net.corda.testutils.services.SimpleJsonMarshallingService
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.MarshallingService

/**
 * Corda normally takes requests via its API in the form of JSON-formatted strings, which are converted by Corda into
 * an RPCRequestData interface. This class represents the equivalent for the FakeCorda.
 *
 * @clientRequestId the id which uniquely identifies a request
 * @flowClassName the name of the flow class to run
 * @requestData the data for the request
 */
data class RPCRequestDataMock(val clientRequestId : String, val flowClassName: String, val requestData : String) {

    companion object {
        private val jms = SimpleJsonMarshallingService()

        /**
         * Constructs an RPC request from a single JSON-formatted string.
         */
        fun fromJSonString(input : String) = jms.parse(input, HttpStartFlow::class.java).httpStartFlow

        /**
         * Constructs an RPCRequest from strongly-typed fields.
         *
         * @clientRequestId the id which uniquely identifies a request
         * @flowClass the flow class to run
         * @requestData the data for the request; this will be converted to a string before being converted back again
         * on consumption.
         */
        fun <T : Any> fromData(clientRequestId: String, flowClass: Class<out RPCStartableFlow>, requestData: T): RPCRequestDataMock =
            RPCRequestDataMock(clientRequestId, flowClass.name, jms.format(requestData))
    }

    /**
     * Converts this into Corda's RPCRequestData.
     */
    fun toRPCRequestData() : RPCRequestData {
        return object : RPCRequestData {
            override fun getRequestBody(): String {
                return requestData
            }

            override fun <T> getRequestBodyAs(marshallingService: MarshallingService, clazz: Class<T>): T {
                return marshallingService.parse(requestData, clazz)
            }

            override fun <T> getRequestBodyAsList(marshallingService: MarshallingService, clazz: Class<T>): List<T> {
                return marshallingService.parseList(requestData, clazz)
            }

        }
    }
}
