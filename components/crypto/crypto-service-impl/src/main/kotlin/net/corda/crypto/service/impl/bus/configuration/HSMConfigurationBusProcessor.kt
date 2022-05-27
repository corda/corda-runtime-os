package net.corda.crypto.service.impl.bus.configuration

import net.corda.crypto.core.CryptoTenants
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
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.slf4j.Logger
import java.time.Instant
import java.util.concurrent.CompletableFuture

class HSMConfigurationBusProcessor(
    private val hsmService: HSMService
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

    override fun onNext(request: HSMConfigurationRequest, respFuture: CompletableFuture<HSMConfigurationResponse>) {
        try {
            logger.info("Handling {} for tenant {}", request.request::class.java.name, request.context.tenantId)
            val response = getHandler(request.request::class.java, hsmService)
                .handle(request.context, request.request)
            val result = HSMConfigurationResponse(createResponseContext(request), response)
            logger.debug(
                "Handled {} for tenant {} with {}",
                request.request::class.java.name,
                request.context.tenantId,
                if (result.response != null) result.response::class.java.name else "null"
            )
            respFuture.complete(result)
        } catch (e: Throwable) {
            val message = "Failed to handle ${request.request::class.java} for tenant ${request.context.tenantId}"
            logger.error(message, e)
            respFuture.completeExceptionally(CryptoServiceLibraryException(message, e))
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