package net.corda.crypto.client

import net.corda.crypto.CryptoConsts
import net.corda.crypto.CryptoPublishResult
import net.corda.crypto.HSMRegistrationClient
import net.corda.data.crypto.config.HSMConfig
import net.corda.data.crypto.wire.registration.hsm.AddHSMCommand
import net.corda.data.crypto.wire.registration.hsm.AssignHSMCommand
import net.corda.data.crypto.wire.registration.hsm.AssignSoftHSMCommand
import net.corda.data.crypto.wire.registration.hsm.HSMRegistrationRequest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger

internal class HSMRegistrationClientImpl(
    private val publisher: Publisher
) : HSMRegistrationClient {
    companion object {
        private val logger = contextLogger()
    }

    override fun putHSM(config: HSMConfig): CryptoPublishResult {
        logger.info(
            "Publishing {}(serviceName={},label={},byo={},categories=[{}])",
            AddHSMCommand::class.java.simpleName,
            config.info.serviceName,
            config.info.hsmLabel,
            config.info.byoTenantId,
            config.info.categories.joinToString()
        )
        return publish(
            CryptoConsts.CLUSTER_TENANT_ID,
            AddHSMCommand(config, emptyKeyValuePairList)
        )
    }

    override fun assignHSM(tenantId: String, category: String, defaultSignatureScheme: String): CryptoPublishResult {
        logger.info(
            "Publishing {}(tenant={},category={},defaultSignatureScheme={})",
            AssignHSMCommand::class.java.simpleName,
            tenantId,
            category,
            defaultSignatureScheme
        )
        return publish(
            tenantId,
            AssignHSMCommand(category, defaultSignatureScheme, emptyKeyValuePairList)
        )
    }

    override fun assignSoftHSM(
        tenantId: String,
        category: String,
        passphrase: String,
        defaultSignatureScheme: String
    ): CryptoPublishResult {
        logger.info(
            "Publishing {}(tenant={},category={},defaultSignatureScheme={})",
            AssignSoftHSMCommand::class.java.simpleName,
            tenantId,
            category,
            defaultSignatureScheme
        )
        return publish(
            tenantId,
            AssignSoftHSMCommand(category, passphrase, defaultSignatureScheme, emptyKeyValuePairList)
        )
    }

    private fun publish(tenantId: String, request: Any): CryptoPublishResult {
        val envelope = createRequest(tenantId, request)
        publisher.publish(
            listOf(
                Record(Schemas.Crypto.HSM_REGISTRATION_MESSAGE_TOPIC, CryptoConsts.CLUSTER_TENANT_ID, envelope)
            )
        ).waitAll()
        val result = envelope.context.toCryptoPublishResult()
        logger.debug(
            "Published {}, req-id={}",
            request::class.java.simpleName,
            result.requestId
        )
        return result
    }

    private fun createRequest(tenantId: String, request: Any): HSMRegistrationRequest =
        HSMRegistrationRequest(
            createWireRequestContext<HSMRegistrationClientImpl>(tenantId),
            request
        )
}