package net.corda.messaging.emulation.subscription.rpc
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CompletableFuture


class RPCSendReceiveIntegrationTest {
    private val requestMsg = "request"
    private val responseMsg = "response"
    private var receivedRequest: String? = null
    private val topicService = TopicServiceImpl()
    private val rpcTopicService = RPCTopicServiceImpl()
    private val subscriptionFactory: SubscriptionFactory = InMemSubscriptionFactory(topicService, rpcTopicService)
    private val publisherFactory: PublisherFactory= CordaPublisherFactory(topicService, rpcTopicService)

    private val responseProcessor = object : RPCResponderProcessor<String,String>{
        override fun onNext(request: String, respFuture: CompletableFuture<String>) {
            receivedRequest = request
            respFuture.complete(responseMsg)
        }
    }

    @Test
    @Timeout(5)
    fun `Test simple request response exchange`(){
        val config = RPCConfig<String,String>(
            groupName ="g1",
            clientName = "c1",
            requestTopic = "t1",
            String::class.java,
            String::class.java
        )

        // Create subscriber for processing requests and start it
        subscriptionFactory.createRPCSubscription(
            rpcConfig = config,
            responderProcessor = responseProcessor).start()

        // Create a sender and send a request
        val sender = publisherFactory.createRPCSender(rpcConfig = config)
        var requestCompletion = sender.sendRequest(requestMsg)

        // wait for the response and validate it
        var response = requestCompletion.get()
        assertThat(response).isEqualTo(responseMsg)

        // assert the original request message was received by the
        // response processor
        assertThat(receivedRequest).isEqualTo(requestMsg)
    }
}