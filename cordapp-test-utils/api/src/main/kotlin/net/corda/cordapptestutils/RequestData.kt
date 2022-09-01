package net.corda.cordapptestutils

import net.corda.cordapptestutils.exceptions.ServiceConfigurationException
import net.corda.cordapptestutils.factories.RequestDataFactory
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCRequestData
import java.util.ServiceLoader

interface RequestData {
    val clientRequestId: String

    val flowClassName: String

    val requestBody: String
    fun toRPCRequestData(): RPCRequestData

    companion object {
        private val factory = ServiceLoader.load(RequestDataFactory::class.java).firstOrNull() ?:
            throw ServiceConfigurationException(RequestDataFactory::class.java)

        fun create(requestId: String, flowClass: Class<out Flow>, request: Any): RequestData
            = factory.create(requestId, flowClass, request)

        fun create(requestId: String, flowClass: String, request: String) : RequestData
            = factory.create(requestId, flowClass, request)

        fun create(jsonInput : String) = factory.create(jsonInput)
    }
}