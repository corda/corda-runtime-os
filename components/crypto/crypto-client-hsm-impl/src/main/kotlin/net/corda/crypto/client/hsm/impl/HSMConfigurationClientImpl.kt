package net.corda.crypto.client.hsm.impl

import net.corda.crypto.component.impl.retry
import net.corda.crypto.component.impl.toClientException
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.createWireRequestContext
import net.corda.crypto.impl.toWire
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoStringResult
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMCategoryInfos
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.HSMInfos
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationRequest
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationResponse
import net.corda.data.crypto.wire.hsm.configuration.commands.LinkHSMCategoriesCommand
import net.corda.data.crypto.wire.hsm.configuration.commands.PutHSMCommand
import net.corda.data.crypto.wire.hsm.configuration.queries.HSMLinkedCategoriesQuery
import net.corda.data.crypto.wire.hsm.configuration.queries.HSMQuery
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.nio.ByteBuffer
import java.time.Duration
import java.util.UUID

class HSMConfigurationClientImpl(
    private val sender: RPCSender<HSMConfigurationRequest, HSMConfigurationResponse>
) {
    companion object {
        private val logger = contextLogger()
    }

    fun putHSM(info: HSMInfo, serviceConfig: ByteArray): String {
        logger.info(
            "Sending {}(configId={},serviceName={},workerLabel={},)",
            PutHSMCommand::class.java.simpleName,
            info.id,
            info.serviceName,
            info.workerLabel
        )
        val request = createRequest(
            request = PutHSMCommand(info, ByteBuffer.wrap(serviceConfig))
        )
        return request.execute(
            Duration.ofSeconds(60),
            CryptoStringResult::class.java
        )!!.value
    }

    fun linkCategories(configId: String, links: List<HSMCategoryInfo>) {
        logger.info(
            "Sending {}(configId={},links.size={})",
            LinkHSMCategoriesCommand::class.java.simpleName,
            configId,
            links.size
        )
        val request = createRequest(
            request = LinkHSMCategoriesCommand(configId, links)
        )
        request.execute(
            Duration.ofSeconds(60),
            CryptoNoContentValue::class.java,
            allowNoContentValue = true
        )
    }

    fun lookup(filter: Map<String, String>): List<HSMInfo> {
        logger.info(
            "Sending {}(filter=[{}])",
            HSMQuery::class.java.simpleName,
            filter.keys.joinToString(),
        )
        val request = createRequest(
            request = HSMQuery(filter.toWire())
        )
        return request.execute(
            Duration.ofSeconds(60),
            HSMInfos::class.java,
            allowNoContentValue = true
        )!!.items
    }

    fun getLinkedCategories(configId: String): List<HSMCategoryInfo> {
        logger.info(
            "Sending {}(configId=[{}])",
            HSMLinkedCategoriesQuery::class.java.simpleName,
            configId,
        )
        val request = createRequest(
            request = HSMLinkedCategoriesQuery(configId)
        )
        return request.execute(
            Duration.ofSeconds(60),
            HSMCategoryInfos::class.java,
            allowNoContentValue = true
        )!!.links
    }

    private fun createRequest(request: Any): HSMConfigurationRequest =
        HSMConfigurationRequest(
            createWireRequestContext<HSMConfigurationClientImpl>(requestId = UUID.randomUUID().toString(), CryptoTenants.CRYPTO),
            request
        )

    @Suppress("ThrowsCount", "UNCHECKED_CAST", "ComplexMethod")
    private fun <RESPONSE> HSMConfigurationRequest.execute(
        timeout: Duration,
        respClazz: Class<RESPONSE>,
        allowNoContentValue: Boolean = false,
        retries: Int = 3
    ): RESPONSE? = try {
        val response = retry(retries, logger) {
            sender.sendRequest(this).getOrThrow(timeout)
        }
        check(
            response.context.requestingComponent == context.requestingComponent &&
                    response.context.tenantId == context.tenantId
        ) {
            "Expected ${context.tenantId} tenant and ${context.requestingComponent} component, but " +
                    "received ${response.response::class.java.name} with ${response.context.tenantId} tenant" +
                    " ${response.context.requestingComponent} component"
        }
        if (response.response::class.java == CryptoNoContentValue::class.java && allowNoContentValue) {
            logger.debug {
                "Received empty response for ${request::class.java.name} for tenant ${context.tenantId}"
            }
            null
        } else {
            check(response.response != null && (response.response::class.java == respClazz)) {
                "Expected ${respClazz.name} for ${context.tenantId} tenant, but " +
                        "received ${response.response::class.java.name} with ${response.context.tenantId} tenant"
            }
            logger.debug {
                "Received response ${respClazz.name} for tenant ${context.tenantId}"
            }
            response.response as RESPONSE
        }
    } catch (e: CordaRPCAPIResponderException) {
        throw e.toClientException()
    } catch (e: Throwable) {
        logger.error("Failed executing ${request::class.java.name} for tenant ${context.tenantId}", e)
        throw e
    }
}