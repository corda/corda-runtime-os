package net.corda.libs.cpiupload

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory

/**
 * Used to create instances of [CpiUploadManager].
 */
interface CpiUploadManagerFactory {
    fun create(
        config: SmartConfig,
        publisherFactory: PublisherFactory,
        subscriptionFactory: SubscriptionFactory
    ): CpiUploadManager
}
