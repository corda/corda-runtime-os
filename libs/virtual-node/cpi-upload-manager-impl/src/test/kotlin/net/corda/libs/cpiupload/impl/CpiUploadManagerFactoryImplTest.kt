package net.corda.libs.cpiupload.impl

import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class CpiUploadManagerFactoryImplTest {
    @Test
    fun `upload manager creation starts pub and sub`() {
        val factory = CpiUploadManagerFactoryImpl()
        val subscription: CompactedSubscription<UploadStatusKey, UploadStatus> = mock()
        val config = SmartConfigImpl.empty()
        val publisher = mock<Publisher>()

        val publisherFactory = mock<PublisherFactory>().apply {
            `when`(createPublisher(any(), any())).thenReturn(publisher)
        }

        val subscriptionFactory = mock<SubscriptionFactory>().apply {
            `when`(createCompactedSubscription<UploadStatusKey, UploadStatus>(any(), any(), any())).thenReturn(
                subscription
            )
        }

        factory.create(config, publisherFactory, subscriptionFactory)

        verify(publisher).start()
        verify(subscription).start()
    }
}
