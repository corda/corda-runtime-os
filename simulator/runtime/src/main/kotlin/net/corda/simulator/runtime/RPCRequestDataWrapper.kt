package net.corda.simulator.runtime

import net.corda.simulator.RequestData
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.marshalling.MarshallingService

/**
 * Corda normally takes requests via its API in the form of JSON-formatted strings, which are converted by Corda into
 * an RestRequestBody interface. This class represents the equivalent for Simulator.
 *
 * @clientRequestId the id which uniquely identifies a request
 * @flowClassName the name of the flow class to run
 * @requestData the data for the request
 */
data class RPCRequestDataWrapper(
    override val clientRequestId : String,
    override val flowClassName: String,
    override val requestData: String)
    : RequestData {

    /**
     * Converts this into Corda's RestRequestBody.
     */
    override fun toRPCRequestData() : RestRequestBody {
        return object : RestRequestBody {
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
