package net.corda.cordapptestutils.factories

import net.corda.cordapptestutils.RequestData
import net.corda.v5.application.flows.Flow

interface RequestDataFactory {

    /**
     * Constructs an RPCRequest from strongly-typed fields.
     *
     * @clientRequestId the id which uniquely identifies a request
     * @flowClass the flow class to run
     * @requestBody the data for the request in JSON format
     */
    fun create(clientRequestId: String, flowClass: String, requestBody: String): RequestData

    /**
     * Constructs an RPCRequest from strongly-typed fields.
     *
     * @clientRequestId the id which uniquely identifies a request
     * @flowClass the flow class to run
     * @requestBody the data for the request; this will be converted to a JSON format string before being
     * converted back again on consumption.
     */
    fun create(clientRequestId: String, flowClass: Class<out Flow>, requestBody: Any): RequestData

    /**
     * Constructs an RPC request from a single JSON-formatted string.
     */
    fun create(jsonString: String) : RequestData
}