package net.corda.messaging.emulation.publisher.factory

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.emulation.publisher.CordaPublisher
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory.Companion.PUBLISHER_INSTANCE_ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class CordaPublisherFactoryTest {
    private val nodeConfig = mock<SmartConfig> {
        on { withFallback(any()) } doReturn this.mock
        on { withValue(any(), any()) } doReturn this.mock
    }
    private val factory = CordaPublisherFactory(mock())

    @Test
    fun `createPublisher returns CordaPublisher`() {
        val publisher = factory.createPublisher(PublisherConfig("client"))

        assertThat(publisher).isInstanceOf(CordaPublisher::class.java)
    }

    @Test
    fun `createPublisher add instance ID to configuration`() {
        factory.createPublisher(PublisherConfig("client", instanceId = 12), nodeConfig)

        verify(nodeConfig).withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef(12))
    }

    @Test
    fun `createPublisher will not add instance ID if null`() {
        factory.createPublisher(PublisherConfig("client"), nodeConfig)

        verify(nodeConfig, never()).withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef(12))
    }
}
