package net.corda.crypto.service.impl.bus

import net.corda.crypto.config.impl.RetryingConfig
import net.corda.crypto.core.CryptoService
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.softhsm.TenantInfoService
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.hsm.registration.commands.AssignSoftHSMCommand
import net.corda.data.crypto.wire.hsm.registration.queries.AssignedHSMQuery
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.CompletableFuture

// This is separate to HSMService so that we can unit test HSMServiceImpl without dealing with RPCResponseProcess
class HSMRegistrationBusProcessor(
    private val tenantInfoService: TenantInfoService,
    private val cryptoService: CryptoService,
    config: RetryingConfig
) : RPCResponderProcessor<HSMRegistrationRequest, HSMRegistrationResponse> {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val executor = CryptoRetryingExecutor(logger, config.maxAttempts.toLong(), config.waitBetweenMills)

    override fun onNext(request: HSMRegistrationRequest, respFuture: CompletableFuture<HSMRegistrationResponse>) {
        try {
            logger.debug {"Handling ${request.request::class.java.name} for tenant ${request.context.tenantId}" }
            val response = executor.executeWithRetry {
                handleRequest(request.request, request.context)
            }
            val result = HSMRegistrationResponse(createResponseContext(request), response)
            logger.debug {
                "Handled ${request.request::class.java.name} for tenant ${request.context.tenantId} with" +
                        " ${if (result.response != null) result.response::class.java.name else "null"}"
            }
            respFuture.complete(result)
        } catch (e: Throwable) {
            logger.error("Failed to handle ${request.request::class.java} for tenant ${request.context.tenantId}", e)
            respFuture.completeExceptionally(e)
        }
    }

    private fun handleRequest(request: Any, context: CryptoRequestContext): Any {
        return when (request) {
            is AssignSoftHSMCommand -> tenantInfoService.populate(context.tenantId, request.category, cryptoService)
            is AssignedHSMQuery -> tenantInfoService.lookup(context.tenantId, request.category) ?: CryptoNoContentValue()
            else -> throw IllegalArgumentException("Unknown request type ${request::class.java.name}")
        }
    }

    private fun createResponseContext(request: HSMRegistrationRequest) = CryptoResponseContext(
        request.context.requestingComponent,
        request.context.requestTimestamp,
        request.context.requestId,
        Instant.now(),
        request.context.tenantId,
        request.context.other
    )
}