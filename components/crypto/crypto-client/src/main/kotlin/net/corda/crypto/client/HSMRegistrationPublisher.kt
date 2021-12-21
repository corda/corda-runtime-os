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

class HSMRegistrationPublisher(
    private val publisher: Publisher
) : HSMRegistrationClient {
    override fun putHSM(config: HSMConfig): CryptoPublishResult =
        publish(
            CryptoConsts.CLUSTER_TENANT_ID,
            AddHSMCommand(config, emptyKeyValuePairList)
        )

    override fun assignHSM(tenantId: String, category: String, defaultSignatureScheme: String): CryptoPublishResult =
        publish(
            tenantId,
            AssignHSMCommand(category, defaultSignatureScheme, emptyKeyValuePairList)
        )

    override fun assignSoftHSM(
        tenantId: String,
        category: String,
        passphrase: String,
        defaultSignatureScheme: String
    ): CryptoPublishResult =
        publish(
            tenantId,
            AssignSoftHSMCommand(category, passphrase, defaultSignatureScheme, emptyKeyValuePairList)
        )

    private fun publish(tenantId: String, request: Any): CryptoPublishResult {
        val envelope = createRequest(tenantId, request)
        publisher.publish(
            listOf(
                Record(Schemas.Crypto.HSM_REGISTRATION_MESSAGE_TOPIC, CryptoConsts.CLUSTER_TENANT_ID, envelope)
            )
        ).waitAll()
        return envelope.context.toCryptoPublishResult()
    }

    private fun createRequest(tenantId: String, request: Any): HSMRegistrationRequest =
        HSMRegistrationRequest(
            createWireRequestContext<HSMRegistrationPublisher>(tenantId),
            request
        )
}