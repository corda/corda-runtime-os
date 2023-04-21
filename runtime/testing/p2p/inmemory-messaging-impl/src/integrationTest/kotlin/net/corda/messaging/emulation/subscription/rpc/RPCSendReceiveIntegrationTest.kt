package net.corda.messaging.emulation.subscription.rpc

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CompletableFuture

@ExtendWith(ServiceExtension::class)
class RPCSendReceiveIntegrationTest {
    private val requestMsg = "request"
    private val responseMsg = "response"
    private var receivedRequest: String? = null

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private val messagingConfig = SmartConfigImpl.empty()
    private val responseProcessor = object : RPCResponderProcessor<String, String> {
        override fun onNext(request: String, respFuture: CompletableFuture<String>) {
            receivedRequest = request
            respFuture.complete(responseMsg)
        }
    }

    @Test
    @Timeout(5)
    fun `Test simple request response exchange`() {
        val config = RPCConfig<String, String>(
            groupName = "g1",
            clientName = "c1",
            requestTopic = "t1",
            String::class.java,
            String::class.java
        )

        // Create subscriber for processing requests and start it
        subscriptionFactory.createRPCSubscription(
            rpcConfig = config,
            responderProcessor = responseProcessor,
            messagingConfig = messagingConfig
        ).start()

        // Create a sender and send a request
        val sender = publisherFactory.createRPCSender(rpcConfig = config, messagingConfig = messagingConfig)
        sender.start()
        val requestCompletion = sender.sendRequest(requestMsg)

        // wait for the response and validate it
        val response = requestCompletion.get()
        assertThat(response).isEqualTo(responseMsg)

        // assert the original request message was received by the
        // response processor
        assertThat(receivedRequest).isEqualTo(requestMsg)
    }
}
