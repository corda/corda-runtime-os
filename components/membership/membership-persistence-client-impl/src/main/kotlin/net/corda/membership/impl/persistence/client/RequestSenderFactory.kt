package net.corda.membership.impl.persistence.client

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory

internal class RequestSenderFactory(
    private val publisherFactory: PublisherFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val clientId: String,
    private val groupId: String,
) {
    fun createSender(
        messagingConfig: SmartConfig,
    ): RequestSender = RequestSender(
        publisherFactory,
        subscriptionFactory,
        messagingConfig,
        clientId,
        groupId,
    )
}
