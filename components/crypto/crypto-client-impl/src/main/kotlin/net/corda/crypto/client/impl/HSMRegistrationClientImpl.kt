package net.corda.crypto.client.impl

import net.corda.crypto.impl.createWireRequestContext
import net.corda.crypto.impl.toWire
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.hsm.registration.commands.AssignHSMCommand
import net.corda.data.crypto.wire.hsm.registration.commands.AssignSoftHSMCommand
import net.corda.data.crypto.wire.hsm.registration.queries.AssignedHSMQuery
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import java.time.Duration

class HSMRegistrationClientImpl(
    private val sender: RPCSender<HSMRegistrationRequest, HSMRegistrationResponse>
) {
    companion object {
        private val logger = contextLogger()
    }

    fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo {
        logger.info(
            "Sending {}(tenant={},category={})",
            AssignHSMCommand::class.java.simpleName,
            tenantId,
            category
        )
        val request = createRequest(
            tenantId = tenantId,
            request = AssignHSMCommand(category, context.toWire())
        )
        val response = request.execute(
            Duration.ofSeconds(60),
            HSMInfo::class.java
        )
        return response!!
    }

    fun assignSoftHSM(
        tenantId: String,
        category: String,
        context: Map<String, String>
    ): HSMInfo {
        logger.info(
            "Sending {}(tenant={},category={})",
            AssignSoftHSMCommand::class.java.simpleName,
            tenantId,
            category
        )
        val request = createRequest(
            tenantId = tenantId,
            request = AssignSoftHSMCommand(category, context.toWire())
        )
        val response = request.execute(
            Duration.ofSeconds(60),
            HSMInfo::class.java
        )
        return response!!
    }

    fun findHSM(tenantId: String, category: String): HSMInfo? {
        logger.info(
            "Sending {}(tenant={},category={})",
            AssignedHSMQuery::class.java.simpleName,
            tenantId,
            category
        )
        val request = createRequest(
            tenantId = tenantId,
            request = AssignedHSMQuery(category)
        )
        return request.execute(
            Duration.ofSeconds(60),
            HSMInfo::class.java,
            allowNoContentValue = true
        )
    }

    private fun createRequest(tenantId: String, request: Any): HSMRegistrationRequest =
        HSMRegistrationRequest(
            createWireRequestContext<HSMRegistrationClientImpl>(tenantId),
            request
        )

    @Suppress("ThrowsCount", "UNCHECKED_CAST", "ComplexMethod")
    private fun <RESPONSE> HSMRegistrationRequest.execute(
        timeout: Duration,
        respClazz: Class<RESPONSE>,
        allowNoContentValue: Boolean = false
    ): RESPONSE? {
        try {
            val response = sender.sendRequest(this).getOrThrow(timeout)
            require(
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
                return null
            }
            require(response.response != null && (response.response::class.java == respClazz)) {
                "Expected ${respClazz.name} for ${context.tenantId} tenant, but " +
                        "received ${response.response::class.java.name} with ${response.context.tenantId} tenant"
            }
            logger.debug {
                "Received response ${respClazz.name} for tenant ${context.tenantId}"
            }
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