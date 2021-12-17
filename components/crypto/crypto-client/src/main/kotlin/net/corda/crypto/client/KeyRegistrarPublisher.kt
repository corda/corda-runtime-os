package net.corda.crypto.client

import net.corda.crypto.clients.CryptoPublishResult
import net.corda.crypto.clients.KeyRegistrarClient
import net.corda.data.crypto.wire.registration.key.GenerateKeyPairCommand
import net.corda.data.crypto.wire.registration.key.KeyRegistrationRequest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas

class KeyRegistrarPublisher(
    private val publisher: Publisher
) : KeyRegistrarClient {

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String>
    ): CryptoPublishResult =
        publish(
            tenantId,
            GenerateKeyPairCommand(tenantId, category, alias, context.toWire())
        )

    private fun publish(tenantId: String, request: Any): CryptoPublishResult {
        val envelope = createRequest(tenantId, request)
        publisher.publish(
            listOf(
                Record(Schemas.Crypto.KEY_REGISTRATION_MESSAGE_TOPIC, tenantId, request)
            )
        ).waitAll()
        return envelope.context.toCryptoPublishResult()
    }

    private fun createRequest(tenantId: String, request: Any): KeyRegistrationRequest =
        KeyRegistrationRequest(
            createWireRequestContext(tenantId),
            request
        )
}