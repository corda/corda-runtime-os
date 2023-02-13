package net.corda.crypto.client.hsm.impl

import net.corda.crypto.component.impl.retry
import net.corda.crypto.component.impl.toClientException
import net.corda.crypto.impl.createWireRequestContext
import net.corda.crypto.impl.toWire
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.hsm.registration.commands.AssignHSMCommand
import net.corda.data.crypto.wire.hsm.registration.commands.AssignSoftHSMCommand
import net.corda.data.crypto.wire.hsm.registration.queries.AssignedHSMQuery
import net.corda.messaging.api.exception.CordaRestAPIResponderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

class HSMRegistrationClientImpl(
    private val sender: RPCSender<HSMRegistrationRequest, HSMRegistrationResponse>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMAssociationInfo {
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
            HSMAssociationInfo::class.java
        )
        return response!!
    }

    fun assignSoftHSM(
        tenantId: String,
        category: String
    ): HSMAssociationInfo {
        logger.info(
            "Sending {}(tenant={},category={})",
            AssignSoftHSMCommand::class.java.simpleName,
            tenantId,
            category
        )
        val request = createRequest(
            tenantId = tenantId,
            request = AssignSoftHSMCommand(category)
        )
        val response = request.execute(
            Duration.ofSeconds(60),
            HSMAssociationInfo::class.java
        )
        return response!!
    }

    fun findHSM(tenantId: String, category: String): HSMAssociationInfo? {
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
            HSMAssociationInfo::class.java,
            allowNoContentValue = true
        )
    }

    private fun createRequest(tenantId: String, request: Any): HSMRegistrationRequest =
        HSMRegistrationRequest(
            createWireRequestContext<HSMRegistrationClientImpl>(requestId = UUID.randomUUID().toString(), tenantId),
            request
        )

    @Suppress("ThrowsCount", "UNCHECKED_CAST", "ComplexMethod")
    private fun <RESPONSE> HSMRegistrationRequest.execute(
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
    } catch (e: CordaRestAPIResponderException) {
        throw e.toClientException()
    } catch (e: Throwable) {
        logger.error("Failed executing ${request::class.java.name} for tenant ${context.tenantId}", e)
        throw e
    }
}