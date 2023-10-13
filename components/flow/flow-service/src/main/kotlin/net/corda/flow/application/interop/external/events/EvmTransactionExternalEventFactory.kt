package net.corda.flow.application.interop.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.Transaction
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

data class EvmTransactionExternalEventParams(
    val transactionOptions: TransactionOptions,
    val functionName: String,
    val to: String,
    val parameters: List<Parameter<*>>,
)

@Component(service = [ExternalEventFactory::class])
class EvmTransactionExternalEventFactory @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService
) : ExternalEventFactory<EvmTransactionExternalEventParams, EvmResponse, String> {
    override val responseType: Class<EvmResponse> = EvmResponse::class.java

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EvmResponse): String {
        return response.payload
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: EvmTransactionExternalEventParams
    ): ExternalEventRecord {
        val transaction = Transaction.newBuilder()
            .setFunction(parameters.functionName)
            .setOptions(parameters.transactionOptions.toAvro())
            .setParameters(parameters.parameters.toAvro())
            .build()

        val request = EvmRequest.newBuilder()
            .setRpcUrl(parameters.transactionOptions.rpcUrl)
            .setFrom(parameters.transactionOptions.from ?: "")
            .setTo(parameters.to)
            .setReturnType(String::class.java.name)
            .setPayload(transaction)
            .build()

        return ExternalEventRecord(
            topic = Schemas.Interop.EVM_REQUEST,
            payload = request
        )
    }

    private fun TransactionOptions.toAvro(): net.corda.data.interop.evm.request.TransactionOptions {
        return net.corda.data.interop.evm.request.TransactionOptions(
            gasLimit.toString(),
            value.toString(),
            maxFeePerGas.toString(),
            maxPriorityFeePerGas.toString()
        )
    }

    private fun List<Parameter<*>>.toAvro() = map { it.toAvro() }

    private fun Parameter<*>.toAvro(): net.corda.data.interop.evm.request.Parameter {
        return net.corda.data.interop.evm.request.Parameter(name, type.name, jsonMarshallingService.format(value))
    }
}