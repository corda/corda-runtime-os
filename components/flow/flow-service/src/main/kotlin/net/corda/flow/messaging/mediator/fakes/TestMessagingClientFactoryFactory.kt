package net.corda.flow.messaging.mediator.fakes

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MessagingClientFactory

/**
 * Factory for creating multi-source event mediator messaging clients.
 */
class TestMessagingClientFactoryFactory(
    private val messageBus: TestMessageBus,
) {

    companion object {
        const val SEND_TIME_MS = "sendTimeMs"
    }
    fun createMessageBusClientFactory(
        id: String,
        messageBusConfig: SmartConfig,
    ) = TestMessageBusClientFactory(
        id,
        messageBusConfig,
        messageBus,
    )

    fun createRPCClientFactory(
        id: String,
        messageBusConfig: SmartConfig,
    ) = TestRpcClientFactory(
        id,
        messageBusConfig,
    )

    class TestMessageBusClientFactory(
        private val id: String,
        private val messageBusConfig: SmartConfig,
        private val messageBus: TestMessageBus
    ): MessagingClientFactory {

        override fun create(config: MessagingClientConfig): MessagingClient {
            return TestMessageBusClient(
                id,
                messageBusConfig.getLong(SEND_TIME_MS),
                messageBus,
            )
        }
    }

    class TestMessageBusClient(
        override val id: String,
        private val sendTimeMs: Long,
        private val messageBus: TestMessageBus
    ) : MessagingClient {

        override fun send(message: MediatorMessage<*>): MediatorMessage<*> {
            Thread.sleep(sendTimeMs)
            val topic = message.getProperty<String>(MessagingClient.MSG_PROP_ENDPOINT)
            messageBus.send(topic, message)
            return MediatorMessage(null)
        }

        override fun close() {
            // Nothing to do
        }
    }
}
