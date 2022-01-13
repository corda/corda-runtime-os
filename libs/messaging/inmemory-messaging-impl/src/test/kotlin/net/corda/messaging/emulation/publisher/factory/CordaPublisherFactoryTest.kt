package net.corda.messaging.emulation.publisher.factory

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.emulation.publisher.CordaPublisher
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
    private val factory = CordaPublisherFactory(mock(), mock(), mock())

    @Test
    fun `createPublisher returns CordaPublisher`() {
        val publisher = factory.createPublisher(PublisherConfig("client"))

        assertThat(publisher).isInstanceOf(CordaPublisher::class.java)
    }

    @Test
    fun `createPublisher add instance ID to configuration`() {
        factory.createPublisher(PublisherConfig("client", instanceId = 12), nodeConfig)

        verify(nodeConfig).withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(12))
    }

    @Test
    fun `createPublisher will not add instance ID if null`() {
        factory.createPublisher(PublisherConfig("client"), nodeConfig)

        verify(nodeConfig, never()).withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(12))
    }

    @Test
    fun `createRPCSender returns an instance of the RPCSender`() {
        val sender = factory.createRPCSender(RPCConfig("g1", "c1", "t1", String::class.java, String::class.java))
        assertThat(sender).isInstanceOf(RPCSender::class.java)
    }
}
