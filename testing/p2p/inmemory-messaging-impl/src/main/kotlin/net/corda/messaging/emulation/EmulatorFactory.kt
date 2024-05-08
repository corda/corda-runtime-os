package net.corda.messaging.emulation

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.Resource
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.emulation.http.HttpServiceImpl
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl

object EmulatorFactory {
    interface Emulator : Resource {
        val subscriptionFactory: SubscriptionFactory
        val publisherFactory: PublisherFactory
        val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    }
    private class EmulatorImpl(
        override val subscriptionFactory: SubscriptionFactory,
        override val publisherFactory: PublisherFactory,
        override val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
        private val topic: AutoCloseable,
    ) : Emulator {
        override fun close() {
            topic.close()
        }
    }
    fun create(lifecycleCoordinatorFactory: LifecycleCoordinatorFactory): Emulator {
        val topicService = TopicServiceImpl()
        val httpServiceImpl = HttpServiceImpl()
        val rpcTopicService = RPCTopicServiceImpl()
        val subscriptionFactory =
            InMemSubscriptionFactory(
                topicService,
                rpcTopicService,
                lifecycleCoordinatorFactory,
                httpServiceImpl,
            )
        val publisherFactory =
            CordaPublisherFactory(
                topicService,
                rpcTopicService,
                lifecycleCoordinatorFactory,
                httpServiceImpl,
            )
        return EmulatorImpl(
            subscriptionFactory,
            publisherFactory,
            lifecycleCoordinatorFactory,
            topicService,
        )
    }
}
