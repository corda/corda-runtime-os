package net.corda.cordapptestutils.internal

import net.corda.cordapptestutils.RequestData
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.marshalling.MarshallingService

/**
 * Corda normally takes requests via its API in the form of JSON-formatted strings, which are converted by Corda into
 * an RPCRequestData interface. This class represents the equivalent for Simulator.
 *
 * @clientRequestId the id which uniquely identifies a request
 * @flowClassName the name of the flow class to run
 * @requestData the data for the request
 */
data class RPCRequestDataWrapper(
    override val clientRequestId : String,
    override val flowClassName: String,
    override val requestBody: String)
    : RequestData {

    /**
     * Converts this into Corda's RPCRequestData.
     */
    override fun toRPCRequestData() : RPCRequestData {
        return object : RPCRequestData {
            override fun getRequestBody(): String {
                return requestBody
            }

            override fun <T> getRequestBodyAs(marshallingService: MarshallingService, clazz: Class<T>): T {
                return marshallingService.parse(requestBody, clazz)
            }

            override fun <T> getRequestBodyAsList(marshallingService: MarshallingService, clazz: Class<T>): List<T> {
                return marshallingService.parseList(requestBody, clazz)
            }

        }
    }
}
