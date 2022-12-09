package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.config.impl.hsmRegistrationBusProcessor
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.impl.retrying.BackoffStrategy
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.impl.CryptoRequestHandler
import net.corda.crypto.service.impl.getHandlerForRequest
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.hsm.registration.commands.AssignHSMCommand
import net.corda.data.crypto.wire.hsm.registration.commands.AssignSoftHSMCommand
import net.corda.data.crypto.wire.hsm.registration.queries.AssignedHSMQuery
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.time.Instant
import java.util.concurrent.CompletableFuture

class HSMRegistrationBusProcessor(
    hsmService: HSMService,
    event: ConfigChangedEvent
) : RPCResponderProcessor<HSMRegistrationRequest, HSMRegistrationResponse> {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private val config = event.config.toCryptoConfig().hsmRegistrationBusProcessor()

    private val executor = CryptoRetryingExecutor(
        logger,
        BackoffStrategy.createBackoff(config.maxAttempts, config.waitBetweenMills)
    )

    private val handlers = setOf<CryptoRequestHandler<*, out Any>>(
        AssignHSMCommandHandler(hsmService),
        AssignSoftHSMCommandHandler(hsmService),
        AssignedHSMQueryHandler(hsmService)
    ).associateBy {
        it.requestClass
    }

    @Suppress("UNCHECKED_CAST")
    override fun onNext(request: HSMRegistrationRequest, respFuture: CompletableFuture<HSMRegistrationResponse>) {
        try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val requestType = request.request::class.java
            val handler = handlers.getHandlerForRequest(requestType)
            val response = executor.executeWithRetry {
                handler.handle(request.request, request.context)
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

    private fun createResponseContext(request: HSMRegistrationRequest) = CryptoResponseContext(
        request.context.requestingComponent,
        request.context.requestTimestamp,
        request.context.requestId,
        Instant.now(),
        request.context.tenantId,
        request.context.other
    )

    private class AssignHSMCommandHandler(
        private val hsmService: HSMService
    ) : CryptoRequestHandler<AssignHSMCommand, HSMAssociationInfo> {
        override val requestClass = AssignHSMCommand::class.java

        override fun handle(request: AssignHSMCommand, context: CryptoRequestContext): HSMAssociationInfo =
            hsmService.assignHSM(context.tenantId, request.category, request.context.toMap())
    }

    private class AssignSoftHSMCommandHandler(
        private val hsmService: HSMService
    ) : CryptoRequestHandler<AssignSoftHSMCommand, HSMAssociationInfo> {
        override val requestClass = AssignSoftHSMCommand::class.java

        override fun handle(request: AssignSoftHSMCommand, context: CryptoRequestContext): HSMAssociationInfo =
            hsmService.assignSoftHSM(context.tenantId, request.category)
    }

    private class AssignedHSMQueryHandler(
        private val hsmService: HSMService
    ) : CryptoRequestHandler<AssignedHSMQuery, Any> {
        override val requestClass = AssignedHSMQuery::class.java

        override fun handle(request: AssignedHSMQuery, context: CryptoRequestContext): Any =
            hsmService.findAssignedHSM(context.tenantId, request.category) ?: CryptoNoContentValue()
    }
}