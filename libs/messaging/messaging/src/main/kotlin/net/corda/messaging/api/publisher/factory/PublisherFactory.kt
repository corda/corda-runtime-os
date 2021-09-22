package net.corda.messaging.api.publisher.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.subscription.factory.config.RPCConfig

/**
 * Interface for creating publishers of events. Only used for producers of events. Not used by consumers.
 * This can be injected as an OSGi Service
 */
interface PublisherFactory {

    /**
     * Create a publisher which publishes to a topic with a given [publisherConfig] and map of [nodeConfig].
     * @return A publisher of events.
     * @throws CordaMessageAPIException Exception in generating a Publisher.
     */
    fun createPublisher(
        publisherConfig: PublisherConfig,
        nodeConfig: Config = ConfigFactory.empty()
    ): Publisher

    /**
     * Create an instance of the [RPCSender]
     *
     * The RPC pattern provides and unreliable RPC mechanism across the message bus. This handles the sender side
     * of the pattern. The sender can post messages to the bus and a future is given back to the client. The future
     * is completed when the response is received. It may also error if the response fails. Timeouts should be handled
     * by the client
     *
     * The client is responsible for retries
     *
     * The responder side can be found in [SubscriptionFactory] under [createRPCSubscription]
     *
     * @param rpcConfig configuration object used to initialize the subscription
     * @param nodeConfig other configuration settings if needed
     */
    fun <TREQ: Any, TRESP: Any> createRPCSender(
        rpcConfig: RPCConfig<TREQ, TRESP>,
        nodeConfig: Config = ConfigFactory.empty()
    ): RPCSender<TREQ, TRESP>


}
