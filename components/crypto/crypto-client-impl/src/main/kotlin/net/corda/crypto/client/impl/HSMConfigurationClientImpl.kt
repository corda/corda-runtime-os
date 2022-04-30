package net.corda.crypto.client.impl

import net.corda.crypto.core.CryptoConsts
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationRequest
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationResponse
import net.corda.data.crypto.wire.hsm.configuration.commands.PutHSMCommand
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

class HSMConfigurationClientImpl(
    private val sender: RPCSender<HSMConfigurationRequest, HSMConfigurationResponse>
) {
    companion object {
        private val logger = contextLogger()
    }

    fun putHSM(config: HSMConfig) {
        logger.info(
            "Publishing {}(serviceName={},workerLabel={},)",
            PutHSMCommand::class.java.simpleName,
            config.info.serviceName,
            config.info.workerLabel
        )
        val request = createRequest(
            tenantId = CryptoConsts.CLUSTER_TENANT_ID,
            request = PutHSMCommand(config)
        )
        request.execute(CryptoNoContentValue::class.java, allowNoContentValue = true)
    }

    private fun createRequest(tenantId: String, request: Any): HSMConfigurationRequest =
        HSMConfigurationRequest(
            createWireRequestContext<HSMConfigurationClientImpl>(tenantId),
            request
        )

    @Suppress("ThrowsCount", "UNCHECKED_CAST", "ComplexMethod")
    private fun <RESPONSE> HSMConfigurationRequest.execute(
        respClazz: Class<RESPONSE>,
        allowNoContentValue: Boolean = false
    ): RESPONSE? {
        while (true) {
            try {
                val response = sender.sendRequest(this).getOrThrow()
                require(
                    response.context.requestingComponent == context.requestingComponent &&
                            response.context.tenantId == context.tenantId
                ) {
                    "Expected ${context.tenantId} tenant and ${context.requestingComponent} component, but " +
                            "received ${response.response::class.java.name} with ${response.context.tenantId} tenant" +
                            " ${response.context.requestingComponent} component"
                }
                if (response.response::class.java == CryptoNoContentValue::class.java && allowNoContentValue) {
                    logger.debug(
                        "Received empty response for {} for tenant {}",
                        request::class.java.name,
                        context.tenantId
                    )
                    return null
                }
                require(response.response != null && (response.response::class.java == respClazz)) {
                    "Expected ${respClazz.name} for ${context.tenantId} tenant, but " +
                            "received ${response.response::class.java.name} with ${response.context.tenantId} tenant"
                }
                logger.debug("Received response {} for tenant {}", respClazz.name, context.tenantId)
                return response.response as RESPONSE
            } catch (e: CryptoServiceLibraryException) {
                logger.error("Failed executing ${request::class.java.name} for tenant ${context.tenantId}", e)
                throw e
            } catch (e: Throwable) {
                val message = "Failed executing ${request::class.java.name} for tenant ${context.tenantId}"
                logger.error(message, e)
                throw CryptoServiceLibraryException(message, e)
            }
        }
    }
}