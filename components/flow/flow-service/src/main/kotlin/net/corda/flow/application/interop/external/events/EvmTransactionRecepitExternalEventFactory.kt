package net.corda.flow.application.interop.external.events

import java.math.BigInteger
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.application.interop.evm.Log
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

data class EvmTransactionReceiptExternalEventParams(
    val options: EvmOptions,
    val hash: String,
)

@Component(service = [ExternalEventFactory::class])
class EvmTransactionReceiptExternalEventFactory @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService
) : ExternalEventFactory<EvmTransactionReceiptExternalEventParams, EvmResponse, TransactionReceipt> {
    override val responseType: Class<EvmResponse> = EvmResponse::class.java

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EvmResponse): TransactionReceipt {
        println("EvmTransactionReceiptExternalEventFactory: resumeWith")
        val receipt = jsonMarshallingService.parse(response.payload.toString(), net.corda.data.interop.evm.response.TransactionReceipt::class.java)
        return receipt.toCorda()
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: EvmTransactionReceiptExternalEventParams
    ): ExternalEventRecord {
        val transactionReceipt = GetTransactionReceipt.newBuilder()
            .setTransactionHash(parameters.hash)
            .build()
        val request = EvmRequest.newBuilder()
            .setRpcUrl(parameters.options.rpcUrl)
            .setFrom(parameters.options.from)
            .setTo("")
            .setReturnType(TransactionReceipt::class.java.name)
            .setPayload(transactionReceipt)
            .setFlowExternalEventContext(flowExternalEventContext)
            .build()
        return ExternalEventRecord(
            topic = Schemas.Interop.EVM_REQUEST,
            payload = request
        )
    }

    private fun net.corda.data.interop.evm.response.TransactionReceipt.toCorda(): TransactionReceipt {
        return TransactionReceipt(
            blockHash,
            BigInteger(blockNumber),
            contractAddress,
            BigInteger(cumulativeGasUsed),
            BigInteger(effectiveGasPrice),
            from,
            BigInteger(gasUsed),
            logs.toCorda(),
            logsBloom,
            status,
            to,
            transactionHash,
            BigInteger(transactionIndex),
            type,
        )
    }

    private fun List<net.corda.data.interop.evm.response.Log>.toCorda() = map { it.toCorda() }

    private fun net.corda.data.interop.evm.response.Log.toCorda(): Log {
        return Log(
            address,
            topics,
            data,
            BigInteger.valueOf(blockNumber.toLong()),
            transactionHash,
            BigInteger.valueOf(transactionIndex.toLong()),
            blockHash,
            logIndex,
            removed
        )
    }
}