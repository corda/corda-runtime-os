package net.corda.flow.application.interop

import co.paralleluniverse.fibers.Suspendable
import net.corda.flow.application.interop.external.events.EvmCallExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmCallExternalEventParams
import net.corda.flow.application.interop.external.events.EvmTransactionExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmTransactionExternalEventParams
import net.corda.flow.application.interop.external.events.EvmTransactionReceiptExternalEventFactory
import net.corda.flow.application.interop.external.events.EvmTransactionReceiptExternalEventParams
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.TransactionReceipt
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions
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
        return try {
            @Suppress("UNCHECKED_CAST")
            externalEventExecutor.execute(
                EvmCallExternalEventFactory::class.java,
                EvmCallExternalEventParams(
                    options, to, functionName, returnType, parameters
                )
            ) as T
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Incorrect type received for call on $functionName.", e)
        }
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
        return try {
            externalEventExecutor.execute(
                EvmTransactionExternalEventFactory::class.java,
                EvmTransactionExternalEventParams(
                    transactionOptions = options,
                    functionName = functionName,
                    to = to,
                    parameters = parameters
                )
            )
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Incorrect type received for call on $functionName.", e)
        }
    }

    @Suspendable
    override fun getTransactionReceipt(
        hash: String,
        options: EvmOptions,
    ): TransactionReceipt {
        return try {
            externalEventExecutor.execute(
                EvmTransactionReceiptExternalEventFactory::class.java,
                EvmTransactionReceiptExternalEventParams(
                    options, hash
                )
            )
        } catch (e: ClassCastException) {
            throw CordaRuntimeException("Wrong type returned for call to TransactionReceipt.", e)
        }
    }
}
