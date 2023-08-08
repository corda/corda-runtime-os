package net.corda.flow.application.interop

import co.paralleluniverse.fibers.Suspendable
import java.math.BigInteger
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.request.Call
import net.corda.data.interop.evm.request.GetTransactionReceipt
import net.corda.data.interop.evm.request.Transaction
import net.corda.flow.application.interop.external.events.EvmExternalEventParams
import net.corda.flow.application.interop.external.events.EvmQueryExternalEventFactory
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Log
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [EvmService::class, UsedByFlow::class],
    property = [CORDA_SYSTEM_SERVICE],
    scope = PROTOTYPE
)
class EvmServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
) : EvmService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun <T : Any> call(
        functionName: String,
        to: String,
        options: CallOptions,
        returnType: Class<T>,
        vararg parameters: Parameter<*>,
    ): T {
        return call(functionName, to, options, returnType, parameters.toList())
    }

    @Suspendable
    override fun <T : Any> call(
        functionName: String,
        to: String,
        options: CallOptions,
        returnType: Class<T>,
        parameters: List<Parameter<*>>,
    ): T {
        return doRequest(
            options.rpcUrl,
            options.from,
            to,
            Call(functionName, options.toAvro(), parameters.toAvro()),
            returnType
        )
    }

    @Suspendable
    override fun transaction(
        functionName: String,
        to: String,
        options: TransactionOptions,
        vararg parameters: Parameter<*>,
    ): String {
        return transaction(functionName, to, options, parameters.toList())
    }

    @Suspendable
    override fun transaction(
        functionName: String,
        to: String,
        options: TransactionOptions,
        parameters: List<Parameter<*>>,
    ): String {
        return doRequest(
            options.rpcUrl,
            options.from,
            to,
            Transaction(functionName, options.toAvro(), parameters.toAvro())
        )
    }

    @Suspendable
    override fun getTransactionReceipt(
        hash: String,
        options: EvmOptions,
    ): TransactionReceipt {
        val response = doRequest<String>(options.rpcUrl, payload = GetTransactionReceipt(hash))
        val receipt =
            jsonMarshallingService.parse(response, net.corda.data.interop.evm.response.TransactionReceipt::class.java)
        return receipt.toCorda()
    }

    private inline fun <reified T> doRequest(
        rpcUrl: String,
        from: String? = null,
        to: String = "",
        payload: Any,
    ) = doRequest<T>(rpcUrl, from, to, payload, T::class.java)

    private fun <T> doRequest(
        rpcUrl: String,
        from: String? = null,
        to: String = "",
        payload: Any,
        returnType: Class<*>
    ): T {
        val request = EvmRequest.newBuilder()
            .setRpcUrl(rpcUrl)
            .setFrom(from ?: "")
            .setTo(to)
            .setReturnType(returnType.name)
            .setPayload(payload)
            .build()
        return try {
            @Suppress("UNCHECKED_CAST")
            externalEventExecutor.execute(
                EvmQueryExternalEventFactory::class.java,
                EvmExternalEventParams(
                    request
                )
            ) as T
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Incorrect type received for request $request.", e)
        }
    }

    private fun TransactionOptions.toAvro(): net.corda.data.interop.evm.request.TransactionOptions {
        return net.corda.data.interop.evm.request.TransactionOptions(
            gasLimit.toString(),
            value.toString(),
            maxFeePerGas.toString(),
            maxPriorityFeePerGas.toString()
        )
    }

    private fun CallOptions.toAvro(): net.corda.data.interop.evm.request.CallOptions {
        return net.corda.data.interop.evm.request.CallOptions(blockNumber)
    }

    private fun List<Parameter<*>>.toAvro() = map { it.toAvro() }

    private fun Parameter<*>.toAvro(): net.corda.data.interop.evm.request.Parameter {
        return net.corda.data.interop.evm.request.Parameter(name, type.name, jsonMarshallingService.format(value))
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
