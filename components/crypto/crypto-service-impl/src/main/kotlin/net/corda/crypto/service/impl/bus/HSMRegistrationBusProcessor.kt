package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.config.impl.hsmRegistrationBusProcessor
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.impl.WireProcessor
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
import net.corda.v5.base.exceptions.BackoffStrategy
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.time.Instant
import java.util.concurrent.CompletableFuture

class HSMRegistrationBusProcessor(
    private val hsmService: HSMService,
    event: ConfigChangedEvent
) : WireProcessor(handlers), RPCResponderProcessor<HSMRegistrationRequest, HSMRegistrationResponse> {
    companion object {
        private val logger: Logger = contextLogger()
        private val handlers = mapOf<Class<*>, Class<out Handler<out Any>>>(
            AssignHSMCommand::class.java to AssignHSMCommandHandler::class.java,
            AssignSoftHSMCommand::class.java to AssignSoftHSMCommandHandler::class.java,
            AssignedHSMQuery::class.java to AssignedHSMQueryHandler::class.java,
        )
    }

    private val config = event.config.toCryptoConfig().hsmRegistrationBusProcessor()

    private val executor = CryptoRetryingExecutor(
        logger,
        BackoffStrategy.createBackoff(config.maxAttempts, config.waitBetweenMills)
    )

    override fun onNext(request: HSMRegistrationRequest, respFuture: CompletableFuture<HSMRegistrationResponse>) {
        try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val handler = getHandler(request.request::class.java, hsmService)
            val response = executor.executeWithRetry {
                handler.handle(request.context, request.request)
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
    ) : Handler<AssignHSMCommand> {
        override fun handle(context: CryptoRequestContext, request: AssignHSMCommand): Any {
            return hsmService.assignHSM(context.tenantId, request.category, request.context.toMap())
        }
    }

    private class AssignSoftHSMCommandHandler(
        private val hsmService: HSMService
    ) : Handler<AssignSoftHSMCommand> {
        override fun handle(context: CryptoRequestContext, request: AssignSoftHSMCommand): Any {
            return hsmService.assignSoftHSM(context.tenantId, request.category)
        }
    }

    private class AssignedHSMQueryHandler(
        private val hsmService: HSMService
    ) : Handler<AssignedHSMQuery> {
        override fun handle(context: CryptoRequestContext, request: AssignedHSMQuery): Any {
            val result = hsmService.findAssignedHSM(context.tenantId, request.category)
            return if(result == null) {
                CryptoNoContentValue()
            } else {
                HSMAssociationInfo(result.workerSetId, result.deprecatedAt)
            }
        }
    }
}