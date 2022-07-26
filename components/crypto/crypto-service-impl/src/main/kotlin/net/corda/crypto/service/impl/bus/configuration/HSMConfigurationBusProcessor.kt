package net.corda.crypto.service.impl.bus.configuration

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.config.hsmConfigBusProcessor
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.impl.toMap
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.impl.WireProcessor
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoStringResult
import net.corda.data.crypto.wire.hsm.HSMCategoryInfos
import net.corda.data.crypto.wire.hsm.HSMInfos
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationRequest
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationResponse
import net.corda.data.crypto.wire.hsm.configuration.commands.LinkHSMCategoriesCommand
import net.corda.data.crypto.wire.hsm.configuration.commands.PutHSMCommand
import net.corda.data.crypto.wire.hsm.configuration.queries.HSMLinkedCategoriesQuery
import net.corda.data.crypto.wire.hsm.configuration.queries.HSMQuery
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.v5.base.exceptions.BackoffStrategy
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.time.Instant
import java.util.concurrent.CompletableFuture

class HSMConfigurationBusProcessor(
    private val hsmService: HSMService,
    event: ConfigChangedEvent
) : WireProcessor(handlers),
    RPCResponderProcessor<HSMConfigurationRequest, HSMConfigurationResponse> {
    companion object {
        private val logger: Logger = contextLogger()
        private val handlers = mapOf<Class<*>, Class<out Handler<out Any>>>(
            LinkHSMCategoriesCommand::class.java to LinkHSMCategoriesCommandHandler::class.java,
            PutHSMCommand::class.java to PutHSMCommandHandler::class.java,
            HSMLinkedCategoriesQuery::class.java to HSMLinkedCategoriesQueryHandler::class.java,
            HSMQuery::class.java to HSMQueryHandler::class.java
        )
    }

    private val config = event.config.toCryptoConfig().hsmConfigBusProcessor()

    private val executor = CryptoRetryingExecutor(
        logger,
        BackoffStrategy.createBackoff(config.maxAttempts, config.waitBetweenMills)
    )

    override fun onNext(request: HSMConfigurationRequest, respFuture: CompletableFuture<HSMConfigurationResponse>) {
        try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val handler = getHandler(request.request::class.java, hsmService)
            val response = executor.executeWithRetry {
                handler.handle(request.context, request.request)
            }
            val result = HSMConfigurationResponse(createResponseContext(request), response)
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

    private fun createResponseContext(request: HSMConfigurationRequest) = CryptoResponseContext(
        request.context.requestingComponent,
        request.context.requestTimestamp,
        request.context.requestId,
        Instant.now(),
        request.context.tenantId,
        request.context.other
    )

    private class LinkHSMCategoriesCommandHandler(
        private val hsmService: HSMService
    ) : Handler<LinkHSMCategoriesCommand> {
        override fun handle(context: CryptoRequestContext, request: LinkHSMCategoriesCommand): Any {
            require(context.tenantId == CryptoTenants.CRYPTO) {
                "Illegal tenant id."
            }
            hsmService.linkCategories(request.configId, request.links)
            return CryptoNoContentValue()
        }
    }

    private class PutHSMCommandHandler(
        private val hsmService: HSMService
    ) : Handler<PutHSMCommand> {
        override fun handle(context: CryptoRequestContext, request: PutHSMCommand): Any {
            require(context.tenantId == CryptoTenants.CRYPTO) {
                "Illegal tenant id."
            }
            return CryptoStringResult(
                hsmService.putHSMConfig(request.info, request.serviceConfig.array())
            )
        }
    }

    private class HSMLinkedCategoriesQueryHandler(
        private val hsmService: HSMService
    ) : Handler<HSMLinkedCategoriesQuery> {
        override fun handle(context: CryptoRequestContext, request: HSMLinkedCategoriesQuery): Any {
            require(context.tenantId == CryptoTenants.CRYPTO) {
                "Illegal tenant id."
            }
            return HSMCategoryInfos(
                hsmService.getLinkedCategories(request.configId)
            )
        }
    }

    private class HSMQueryHandler(
        private val hsmService: HSMService
    ) : Handler<HSMQuery> {
        override fun handle(context: CryptoRequestContext, request: HSMQuery): Any {
            require(context.tenantId == CryptoTenants.CRYPTO) {
                "Illegal tenant id."
            }
            return HSMInfos(
                hsmService.lookup(request.filter.toMap())
            )
        }
    }
}