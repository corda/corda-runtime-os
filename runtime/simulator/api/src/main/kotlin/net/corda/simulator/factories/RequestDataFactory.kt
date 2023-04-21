package net.corda.simulator.factories

import net.corda.simulator.RequestData
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.annotations.DoNotImplement

/**
 * Used for constructing [RequestData]. This interface should not be
 * used directly; instead use the [RequestData.create] methods on [RequestData].
 */
@DoNotImplement
interface RequestDataFactory {

    /**
     * Constructs [RequestData] from strongly-typed fields.
     *
     * @clientRequestId The id which uniquely identifies a request.
     * @flowClass The flow class to run.
     * @requestBody The data for the request in JSON format.
     */
    fun create(clientRequestId: String, flowClass: String, requestBody: String): RequestData

    /**
     * Constructs [RequestData] from strongly-typed fields.
     *
     * @clientRequestId The id which uniquely identifies a request.
     * @flowClass The flow class to run.
     * @requestBody The data for the request; this will be converted to a JSON format string before being
     * converted back again on consumption.
     */
    fun create(clientRequestId: String, flowClass: Class<out Flow>, requestBody: Any): RequestData

    /**
     * Constructs [RequestData] from a single JSON-formatted string.
     * @return [RequestData].
     */
    fun create(jsonString: String) : RequestData
}