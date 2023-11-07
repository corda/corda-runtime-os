package net.corda.messaging.mediator.factory

import java.net.http.HttpClient
import java.time.Duration
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.mediator.RPCClient

class RPCClientFactory(
    private val id: String,
    private val cordaSerializationFactory: CordaAvroSerializationFactory,
    private val platformDigestService: PlatformDigestService
): MessagingClientFactory {
    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    override fun create(config: MessagingClientConfig): MessagingClient {
        return RPCClient(
            id,
            cordaSerializationFactory,
            platformDigestService,
            config.onSerializationError,
            httpClient
        )
    }
}
