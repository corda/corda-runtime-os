package net.corda.simulator.runtime

import net.corda.simulator.RequestData
import net.corda.simulator.factories.RequestDataFactory
import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.application.flows.Flow

/**
 * @see [RequestDataFactory] for details.
 */
class RPCRequestDataWrapperFactory : RequestDataFactory {
    private val jms = SimpleJsonMarshallingService()

    private data class HttpStartFlow(val httpStartFlow: RequestData)

    override fun create(clientRequestId: String, flowClass: String, requestBody: String): RequestData =
        RPCRequestDataWrapper(clientRequestId, flowClass, requestBody)

    override fun create(clientRequestId: String, flowClass: Class<out Flow>, requestBody: Any): RequestData =
        RPCRequestDataWrapper(clientRequestId, flowClass.name, jms.format(requestBody))

    override fun create(jsonString : String) = jms.parse(jsonString, HttpStartFlow::class.java).httpStartFlow



}