package net.corda.flow.application.interop.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.Call
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

data class EvmCallExternalEventParams(
    val callOptions: CallOptions,
    val functionName: String,
    val to: String,
    val returnType: Class<*>,
    val parameters: List<Parameter<*>>,
)

@Component(service = [ExternalEventFactory::class])
class EvmCallExternalEventFactory @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService
) : ExternalEventFactory<EvmCallExternalEventParams, EvmResponse, Any> {
    override val responseType: Class<EvmResponse> = EvmResponse::class.java

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EvmResponse): Any {
        return response.payload
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: EvmCallExternalEventParams
    ): ExternalEventRecord {
        val call = Call.newBuilder()
            .setFunction(parameters.functionName)
            .setOptions(parameters.callOptions.toAvro())
            .setParameters(parameters.parameters.toAvro())
            .build()
        val request = EvmRequest.newBuilder()
            .setRpcUrl(parameters.callOptions.rpcUrl)
            .setFrom(parameters.callOptions.from ?: "")
            .setTo(parameters.to)
            .setReturnType(parameters.returnType.name)
            .setPayload(call)
            .setFlowExternalEventContext(flowExternalEventContext)
            .build()

        return ExternalEventRecord(
            topic = Schemas.Interop.EVM_REQUEST,
            payload = request
        )
    }

    private fun CallOptions.toAvro(): net.corda.data.interop.evm.request.CallOptions {
        return net.corda.data.interop.evm.request.CallOptions(blockNumber)
    }

    private fun List<Parameter<*>>.toAvro() = map { it.toAvro() }

    private fun Parameter<*>.toAvro(): net.corda.data.interop.evm.request.Parameter {
        return net.corda.data.interop.evm.request.Parameter(name, type.name, jsonMarshallingService.format(value))
    }
}