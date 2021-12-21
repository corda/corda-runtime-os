package net.corda.crypto.client

import net.corda.crypto.CryptoPublishResult
import net.corda.crypto.HSMLabelMap
import net.corda.crypto.KeyRegistrationClient
import net.corda.data.crypto.wire.registration.key.GenerateKeyPairCommand
import net.corda.data.crypto.wire.registration.key.KeyRegistrationRequest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger

class KeyRegistrationPublisher(
    private val publisher: Publisher,
    private val labelMap: HSMLabelMap
) : KeyRegistrationClient {
    companion object {
        private val logger = contextLogger()
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String>
    ): CryptoPublishResult {
        logger.info(
            "Sending '{}'(tenant={}, category={}, alias={}) command",
            GenerateKeyPairCommand::class.java.name,
            tenantId,
            category,
            alias
        )
        return publish(
            tenantId,
            labelMap.get(tenantId, category),
            GenerateKeyPairCommand(category, alias, context.toWire())
        )
    }

    private fun publish(tenantId: String, hsmLabel: String?, request: Any): CryptoPublishResult {
        val envelope = createRequest(tenantId, hsmLabel, request)
        publisher.publish(
            listOf(
                Record(Schemas.Crypto.KEY_REGISTRATION_MESSAGE_TOPIC, tenantId, envelope)
            )
        ).waitAll()
        return envelope.context.toCryptoPublishResult()
    }

    private fun createRequest(tenantId: String, hsmLabel: String?, request: Any): KeyRegistrationRequest =
        KeyRegistrationRequest(
            createWireRequestContext<KeyRegistrationPublisher>(
                tenantId = tenantId,
                hsmLabel = hsmLabel
            ),
            request
        )
}