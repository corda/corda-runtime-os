package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ClientCertificatePublisherTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val messagingConfiguration = mock<SmartConfig>()
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val publisherFactory = mock<PublisherFactory>()

    private val clientCertificatePublisher = ClientCertificatePublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        mock(),
    )

    @Test
    fun `it will create a domino tile`() {
        assertThat(clientCertificatePublisher.dominoTile).isNotNull
    }
}
