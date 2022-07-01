package net.corda.flow.pipeline.handlers.waiting.crypto

import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.state.crypto.CryptoState
import net.corda.data.flow.state.waiting.SignedBytes
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig.CRYPTO_MAX_RETRIES
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowWaitingForHandler::class])
class SignedBytesWaitingForHandler @Activate constructor(
    @Reference(service = CryptoFlowOpsTransformer::class)
    private val cryptoFlowOpsTransformer: CryptoFlowOpsTransformer
) : FlowWaitingForHandler<SignedBytes> {

    private companion object {
        val log = contextLogger()
    }

    override val type = SignedBytes::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: SignedBytes): FlowContinuation {
        val checkpoint = context.checkpoint
        val cryptoState = checkpoint.cryptoState
        if (cryptoState?.request?.request != null) {
            log.debug {
                "Checking to see if response received for request type " +
                        "${cryptoState.request.request::class} and id ${cryptoState.requestId}"
            }
        }
        val response = cryptoState?.response
        return if (response != null) {
            val exceptionEnvelope = response.exception
            if (exceptionEnvelope == null) {
                context.checkpoint.cryptoState = null
                FlowContinuation.Run(cryptoFlowOpsTransformer.transform(response))
            } else {
                handleErrorResponse(exceptionEnvelope, cryptoState, context)
            }
        } else {
            log.debug { "No response received yet for request id ${cryptoState?.requestId}" }
            FlowContinuation.Continue
        }
    }

    private fun handleErrorResponse(
        exceptionEnvelope: ExceptionEnvelope,
        cryptoState: CryptoState,
        context: FlowEventContext<*>
    ): FlowContinuation {
        val errorMessage = "$exceptionEnvelope returned from the crypto worker"
        // TODO Placeholder code until unification is completed
        return when (exceptionEnvelope.errorType) {
            "USER_ERROR" -> {
                log.error("$errorMessage. Exception: $exceptionEnvelope")
                context.checkpoint.cryptoState = null
                FlowContinuation.Error(CordaRuntimeException(exceptionEnvelope.errorMessage))
            }
            "RETRY" -> {
                handleRetriableError(context.config, exceptionEnvelope, cryptoState, errorMessage, context.checkpoint)
            }
            "PLATFORM_ERROR" -> {
                log.error("$errorMessage. Exception: $exceptionEnvelope")
                throw FlowFatalException(errorMessage, context)
            }
            else -> {
                log.error("Unexpected error type returned from the crypto worker: ${exceptionEnvelope.errorType}")
                throw FlowFatalException(errorMessage, context)
            }
        }.also {
            cryptoState.response = null
        }
    }

    // TODO Placeholder code until unification is completed
    private fun handleRetriableError(
        config: SmartConfig,
        exceptionEnvelope: ExceptionEnvelope,
        cryptoState: CryptoState,
        errorMessage: String,
        checkpoint: FlowCheckpoint
    ): FlowContinuation {
        val retries = cryptoState.retries
        return if (retries >= config.getLong(CRYPTO_MAX_RETRIES)) {
            log.error("$errorMessage. Exceeded max retries.")
            checkpoint.cryptoState = null
            FlowContinuation.Error(CordaRuntimeException(exceptionEnvelope.errorMessage))
        } else {
            log.warn("$errorMessage. Retrying exception after delay. Current retry count $retries.")
            cryptoState.retries = retries.inc()
            FlowContinuation.Continue
        }
    }
}